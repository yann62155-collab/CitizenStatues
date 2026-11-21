package fr.citizen.statues.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.network.PlayerDestinationsPayload;
import fr.citizen.statues.network.VoiceIntentPayload;
import fr.citizen.statues.network.VoiceIntentResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Local, offline French speech recognition for the guide statues.
 *
 * <p>Detection pipeline (all on the player's computer, no audio is stored or sent):
 * <ol>
 *   <li>Microphone frames come from Citizen Voix (16 kHz mono, 20 ms, already denoised).</li>
 *   <li>An adaptive noise floor detects when the player starts and stops talking, so listening ends
 *       ~0.8 s after the sentence instead of after a fixed delay.</li>
 *   <li>Automatic gain control brings quiet voices up to a level Vosk recognises well.</li>
 *   <li>Two recognisers run side by side: free dictation (with alternatives) and one restricted to the
 *       statue's destination names, synonyms and commands. All hypotheses are sent to the server,
 *       which does the fuzzy / phonetic understanding.</li>
 * </ol>
 * The fallback menu opens after three failed attempts, or immediately when the player says "menu".</p>
 */
public final class VoiceRecognitionClient {
    private static final float SAMPLE_RATE = 16_000f;
    private static final String MODEL_NAME = "vosk-model-small-fr-0.22";
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/" + MODEL_NAME + ".zip";
    private static final int MAX_FAILURES = 3;

    // Voice activity detection (20 ms frames)
    private static final int CALIBRATION_FRAMES = 8;          // 160 ms to learn the background noise
    private static final int START_FRAMES = 3;                // 60 ms of voice to consider speech started
    private static final int END_SILENCE_FRAMES = 40;         // 800 ms of silence ends the sentence
    private static final int MIN_SPEECH_FRAMES = 12;          // ignore clicks shorter than 240 ms
    private static final long NO_SPEECH_TIMEOUT_MS = 6_000L;  // nobody talked
    private static final long MAX_CAPTURE_MS = 10_000L;       // hard privacy cap
    private static final double MIN_SPEECH_RMS = 0.006;       // absolute floor (normalised 0..1)
    private static final double SPEECH_OVER_NOISE = 2.6;

    // Automatic gain control
    private static final double AGC_TARGET_RMS = 0.09;
    private static final double AGC_MAX_GAIN = 6.0;

    private static volatile Model model;
    private static volatile boolean busy;
    private static volatile int failures;
    private static volatile PlayerDestinationsPayload fallback;

    private VoiceRecognitionClient() {}

    public static void begin(PlayerDestinationsPayload payload) {
        fallback = payload;
        failures = 0;
        start(payload);
    }

    public static void onServerResult(VoiceIntentResultPayload.Action action, String message) {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            String colour = switch (action) {
                case TELEPORTED -> "§a";
                case CANCELLED, OPEN_MENU -> "§e";
                default -> "§c";
            };
            if (!message.isBlank()) player.displayClientMessage(Component.literal(colour + message), false);
            switch (action) {
                case TELEPORTED, CANCELLED, UNAVAILABLE -> reset();
                case OPEN_MENU -> openMenu();
                case RETRY -> failedAttempt();
            }
        });
    }

    private static void reset() {
        failures = 0;
        fallback = null;
    }

    private static void openMenu() {
        PlayerDestinationsPayload data = fallback;
        reset();
        if (data != null) Minecraft.getInstance().setScreen(
                new PlayerDestinationsScreen(data.statueId(), data.title(), data.destinations()));
    }

    private static void failedAttempt() {
        failures++;
        if (failures >= MAX_FAILURES) {
            openMenu();
        } else {
            PlayerDestinationsPayload data = fallback;
            if (data != null) start(data);
        }
    }

    private static void start(PlayerDestinationsPayload payload) {
        if (busy) return;
        busy = true;
        Minecraft mc = Minecraft.getInstance();
        if (model == null) actionBar("§ePréparation de la reconnaissance vocale française…");
        CompletableFuture.runAsync(() -> {
            try {
                recognize(payload, getModel());
            } catch (CaptureUnavailableException ex) {
                mc.execute(() -> {
                    busy = false;
                    chat("§c" + ex.getMessage() + " Voici la liste des lieux.");
                    openMenu();
                });
            } catch (Exception ex) {
                CitizenStatues.LOGGER.error("French voice recognition failed", ex);
                mc.execute(() -> {
                    busy = false;
                    chat("§cReconnaissance vocale indisponible : " + safeMessage(ex));
                    openMenu();
                });
            }
        });
    }

    // ------------------------------------------------------------------ recognition

    private static void recognize(PlayerDestinationsPayload payload, Model model) throws Exception {
        Class<?> voiceClient;
        try {
            voiceClient = Class.forName("fr.citizen.voice.client.CitizenVoiceClient");
        } catch (ClassNotFoundException missing) {
            throw new CaptureUnavailableException("Citizen Voix doit être installé pour parler au guide.");
        }
        var startCapture = voiceClient.getMethod("setCommandCapture", Consumer.class);
        var stopCapture = voiceClient.getMethod("clearCommandCapture");

        String grammar = buildGrammar(payload.destinations());
        try (Recognizer free = new Recognizer(model, SAMPLE_RATE);
             Recognizer constrained = createConstrained(model, grammar)) {
            free.setMaxAlternatives(3);

            Listener listener = new Listener(free, constrained);
            boolean started = (boolean) startCapture.invoke(null, (Consumer<short[]>) listener::onFrame);
            if (!started) {
                throw new CaptureUnavailableException("Citizen Voix n'est pas connecté ou ton micro n'est pas prêt.");
            }
            actionBar("§a♪ Je t'écoute… dis le nom de ta destination.");
            try {
                listener.done.get(MAX_CAPTURE_MS + 1_000L, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // the hard cap below still applies
            } finally {
                stopCapture.invoke(null);
            }

            List<String> hypotheses = new ArrayList<>();
            synchronized (listener) {
                listener.finished = true; // late frames must never reach a closed recogniser
                collectAlternatives(free.getFinalResult(), hypotheses);
                if (constrained != null) collectText(constrained.getFinalResult(), hypotheses);
            }
            boolean spoke = listener.speechFrames >= MIN_SPEECH_FRAMES;
            List<String> clean = hypotheses.stream()
                    .map(h -> h.replace("[unk]", " ").replaceAll("\\s+", " ").trim())
                    .filter(h -> !h.isBlank())
                    .map(h -> h.length() > 256 ? h.substring(0, 256) : h)
                    .distinct().limit(VoiceIntentPayload.MAX_HYPOTHESES).toList();

            Minecraft.getInstance().execute(() -> {
                busy = false;
                if (Minecraft.getInstance().player == null) return;
                if (clean.isEmpty()) {
                    chat(spoke ? "§cJe n'ai pas compris, parle un peu plus fort et distinctement."
                            : "§cJe n'ai rien entendu. Vérifie ton micro dans Citizen Voix.");
                    failedAttempt();
                    return;
                }
                actionBar("§7Entendu : « " + clean.get(0) + " »");
                PacketDistributor.sendToServer(new VoiceIntentPayload(payload.statueId(), clean));
            });
        }
    }

    private static Recognizer createConstrained(Model model, String grammar) {
        if (grammar == null) return null;
        try {
            return new Recognizer(model, SAMPLE_RATE, grammar);
        } catch (Exception | Error ex) {
            // Some models do not support runtime grammars; free dictation alone still works.
            CitizenStatues.LOGGER.warn("Voice grammar unavailable, using free dictation only: {}", safeMessage(ex));
            return null;
        }
    }

    /** Builds a Vosk grammar with the statue's destinations, synonyms, ordinals and commands. */
    static String buildGrammar(List<String> destinations) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        for (String line : destinations) {
            String[] p = line.split("\\|", 6);
            if (p.length < 2) continue;
            addPhrase(phrases, p[1]);
            if (p.length > 5 && !p[5].isBlank()) for (String syn : p[5].split(";")) addPhrase(phrases, syn);
        }
        if (phrases.isEmpty()) return null;
        for (String carrier : List.of("je veux aller", "emmène moi", "amène moi", "téléporte moi", "je vais",
                "au", "à la", "à l'", "aux", "à", "vers", "le", "la", "les", "du", "de la", "s'il te plaît")) {
            addPhrase(phrases, carrier);
        }
        for (String command : List.of("annuler", "laisse tomber", "menu", "la liste", "le premier", "la première",
                "le deuxième", "la deuxième", "le troisième", "la troisième", "le dernier", "la dernière",
                "numéro un", "numéro deux", "numéro trois", "numéro quatre", "numéro cinq")) {
            addPhrase(phrases, command);
        }
        JsonArray array = new JsonArray();
        phrases.forEach(array::add);
        array.add("[unk]");
        return array.toString();
    }

    private static void addPhrase(Set<String> phrases, String raw) {
        if (raw == null) return;
        String p = Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.FRENCH)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").trim();
        if (!p.isEmpty() && p.length() <= 64) phrases.add(p);
    }

    private static void collectAlternatives(String json, List<String> out) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("alternatives")) {
                for (JsonElement alt : obj.getAsJsonArray("alternatives")) {
                    JsonObject a = alt.getAsJsonObject();
                    if (a.has("text")) out.add(a.get("text").getAsString());
                }
            } else if (obj.has("text")) {
                out.add(obj.get("text").getAsString());
            }
        } catch (Exception ignored) { }
    }

    private static void collectText(String json, List<String> out) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("text")) out.add(obj.get("text").getAsString());
        } catch (Exception ignored) { }
    }

    /** Receives microphone frames on Citizen Voix's capture thread. */
    private static final class Listener {
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final Recognizer free;
        final Recognizer constrained;
        final long startedAt = System.currentTimeMillis();
        volatile boolean finished;

        double noiseFloor = -1;
        int calibration;
        int voicedRun;
        int silenceRun;
        int speechFrames;
        boolean speaking;
        double gain = 1.0;
        long lastPartial;

        Listener(Recognizer free, Recognizer constrained) {
            this.free = free;
            this.constrained = constrained;
        }

        void onFrame(short[] pcm) {
            if (finished) return;
            long now = System.currentTimeMillis();
            double rms = rms(pcm);

            // 1) learn the background noise, then follow it slowly while nobody speaks
            if (calibration < CALIBRATION_FRAMES) {
                // keep the quietest frame, capped so a player who talks immediately is still heard
                noiseFloor = Math.min(0.03, noiseFloor < 0 ? rms : Math.min(noiseFloor, rms));
                calibration++;
            }
            double threshold = Math.max(MIN_SPEECH_RMS, noiseFloor * SPEECH_OVER_NOISE);
            boolean voiced = rms > threshold;
            if (!voiced && !speaking) noiseFloor = noiseFloor * 0.97 + rms * 0.03;

            // 2) speech start / end detection
            if (voiced) {
                voicedRun++;
                silenceRun = 0;
                if (voicedRun >= START_FRAMES) speaking = true;
                if (speaking) speechFrames++;
                // 3) automatic gain: follow the speaking level only, never the background noise
                double wanted = Math.max(1.0, Math.min(AGC_MAX_GAIN, AGC_TARGET_RMS / Math.max(rms, 1e-4)));
                gain = gain * 0.85 + wanted * 0.15;
            } else {
                voicedRun = 0;
                if (speaking) silenceRun++;
            }

            byte[] bytes = toBytes(pcm, gain);
            boolean endpoint;
            synchronized (this) {
                if (finished) return;
                endpoint = free.acceptWaveForm(bytes, bytes.length);
                if (constrained != null) constrained.acceptWaveForm(bytes, bytes.length);
                if (now - lastPartial > 250) {
                    lastPartial = now;
                    showPartial(free.getPartialResult());
                }
            }

            boolean sentenceOver = speaking && speechFrames >= MIN_SPEECH_FRAMES
                    && (silenceRun >= END_SILENCE_FRAMES || (endpoint && silenceRun >= END_SILENCE_FRAMES / 2));
            boolean nobodySpoke = !speaking && now - startedAt > NO_SPEECH_TIMEOUT_MS;
            if (sentenceOver || nobodySpoke || now - startedAt > MAX_CAPTURE_MS) done.complete(null);
        }

        private static void showPartial(String json) {
            try {
                String partial = JsonParser.parseString(json).getAsJsonObject().get("partial").getAsString()
                        .replace("[unk]", "").trim();
                if (!partial.isEmpty()) actionBar("§b♪ " + (partial.length() > 60 ? partial.substring(0, 60) + "…" : partial));
            } catch (Exception ignored) { }
        }
    }

    private static double rms(short[] pcm) {
        double acc = 0;
        for (short v : pcm) acc += (double) v * v;
        return Math.sqrt(acc / Math.max(1, pcm.length)) / 32768.0;
    }

    private static byte[] toBytes(short[] pcm, double gain) {
        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            int v = (int) Math.round(pcm[i] * gain);
            if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
            bytes[i * 2] = (byte) (v & 0xff);
            bytes[i * 2 + 1] = (byte) ((v >>> 8) & 0xff);
        }
        return bytes;
    }

    // ------------------------------------------------------------------ model

    private static Model getModel() throws Exception {
        if (model != null) return model;
        synchronized (VoiceRecognitionClient.class) {
            if (model != null) return model;
            Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("config/citizenstatues/voice");
            Path modelDir = root.resolve(MODEL_NAME);
            Files.createDirectories(root);
            if (!Files.isDirectory(modelDir.resolve("conf"))) {
                actionBar("§eTéléchargement du modèle vocal français (une seule fois, ~40 Mo)…");
                Path zip = root.resolve("french-model.zip");
                Path partial = root.resolve("french-model.zip.part");
                if (!Files.exists(zip)) {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(MODEL_URL))
                            .timeout(Duration.ofMinutes(5)).GET().build();
                    HttpResponse<Path> response = HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NORMAL).build()
                            .send(request, HttpResponse.BodyHandlers.ofFile(partial));
                    if (response.statusCode() != 200) {
                        Files.deleteIfExists(partial);
                        throw new IOException("Téléchargement du modèle refusé (" + response.statusCode() + ")");
                    }
                    Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    extractSafely(zip, root);
                } catch (IOException corrupt) {
                    Files.deleteIfExists(zip); // a broken download will be fetched again next time
                    throw corrupt;
                }
                Files.deleteIfExists(zip);
            }
            model = new Model(modelDir.toAbsolutePath().toString());
            return model;
        }
    }

    private static void extractSafely(Path zip, Path destination) throws IOException {
        Path base = destination.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path target = base.resolve(entry.getName()).normalize();
                if (!target.startsWith(base)) throw new IOException("Archive de modèle invalide");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
        });
    }

    private static void chat(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text), false);
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }

    private static final class CaptureUnavailableException extends IOException {
        CaptureUnavailableException(String message) { super(message); }
    }
}
