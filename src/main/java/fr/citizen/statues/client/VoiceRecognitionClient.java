package fr.citizen.statues.client;

import com.google.gson.JsonParser;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.network.VoiceIntentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Opt-in, local-only French speech recognition. Audio never leaves the player's computer. */
public final class VoiceRecognitionClient {
    private static final float SAMPLE_RATE = 16_000f;
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip";
    private static volatile Model model;
    private static volatile boolean busy;

    private VoiceRecognitionClient() {}

    public static void start(String statueId) {
        if (busy) return;
        busy = true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.player.displayClientMessage(
                Component.literal("§eReconnaissance vocale : préparation du modèle français…"), true));
        CompletableFuture.runAsync(() -> {
            try {
                Model ready = getModel();
                recognize(statueId, ready);
            } catch (Exception ex) {
                CitizenStatues.LOGGER.error("French voice recognition failed", ex);
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§cVoix indisponible : " + safeMessage(ex)), false));
            } finally {
                busy = false;
            }
        });
    }

    private static Model getModel() throws Exception {
        if (model != null) return model;
        synchronized (VoiceRecognitionClient.class) {
            if (model != null) return model;
            Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("config/citizenstatues/voice");
            Path modelDir = root.resolve("vosk-model-small-fr-0.22");
            Files.createDirectories(root);
            if (!Files.isDirectory(modelDir.resolve("conf"))) {
                Path zip = root.resolve("french-model.zip");
                if (!Files.exists(zip)) {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(MODEL_URL))
                            .timeout(Duration.ofMinutes(5)).GET().build();
                    HttpResponse<Path> response = HttpClient.newHttpClient().send(request,
                            HttpResponse.BodyHandlers.ofFile(zip));
                    if (response.statusCode() != 200) throw new IOException("Téléchargement du modèle refusé (" + response.statusCode() + ")");
                }
                extractSafely(zip, root);
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

    private static void recognize(String statueId, Model model) throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) throw new IOException("Microphone 16 kHz indisponible");
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§aParle maintenant : indique le nom de la destination (8 secondes)."), true));
        String transcript = "";
        try (Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            byte[] buffer = new byte[4096];
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (System.nanoTime() < deadline) {
                int count = microphone.read(buffer, 0, buffer.length);
                if (count > 0 && recognizer.acceptWaveForm(buffer, count)) {
                    String text = JsonParser.parseString(recognizer.getResult()).getAsJsonObject()
                            .get("text").getAsString();
                    if (!text.isBlank()) transcript = text;
                }
            }
            String last = JsonParser.parseString(recognizer.getFinalResult()).getAsJsonObject()
                    .get("text").getAsString();
            if (!last.isBlank()) transcript = last;
        } finally {
            microphone.stop();
            microphone.close();
        }
        String finalTranscript = transcript;
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (finalTranscript.isBlank()) {
                player.displayClientMessage(Component.literal("§cJe n'ai rien entendu. Réessaie."), false);
                return;
            }
            player.displayClientMessage(Component.literal("§7Reconnu : §f" + finalTranscript), false);
            PacketDistributor.sendToServer(new VoiceIntentPayload(statueId, finalTranscript));
        });
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }
}
