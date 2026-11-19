package fr.citizen.statues.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.data.StatueGroup;
import fr.citizen.statues.data.StatueProfile;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Small same-origin HTTP control panel. Runs only on the dedicated server;
 * all mutations are checked and persisted by the server-side registry.
 */
public final class WebAdminServer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_LIFETIME_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_BODY_BYTES = 32 * 1024;
    private static final Map<String, Long> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, LoginAttempt> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();

    private static HttpServer server;
    private static ExecutorService executor;
    private static String accessKey;
    private static MinecraftServer minecraftServer;
    private static int port;

    private record LoginAttempt(int failures, long blockedUntil) {}

    private WebAdminServer() {}

    public static synchronized void start(MinecraftServer activeServer, Path configFile) {
        stop();
        try {
            minecraftServer = activeServer;
            Properties config = loadOrCreateConfig(configFile);
            if (!Boolean.parseBoolean(config.getProperty("enabled", "true"))) {
                CitizenStatues.LOGGER.info("CitizenStatues web panel is disabled in {}", configFile);
                return;
            }
            port = Integer.parseInt(config.getProperty("port", "30033").trim());
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("Web panel port must be 1024..65535");
            String bind = config.getProperty("bind", "0.0.0.0").trim();
            accessKey = config.getProperty("accessKey", "").trim();
            if (accessKey.length() < 32) throw new IllegalArgumentException("accessKey missing or too short");

            server = HttpServer.create(new InetSocketAddress(bind, port), 32);
            server.createContext("/", WebAdminServer::handle);
            executor = Executors.newFixedThreadPool(4, task -> {
                Thread thread = new Thread(task, "CitizenStatues-WebAdmin");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            CitizenStatues.LOGGER.info("CitizenStatues web panel listening on {}:{}", bind, port);
            CitizenStatues.LOGGER.warn("CitizenStatues web panel uses plain HTTP. Do not enter its access key on untrusted networks without an HTTPS proxy.");
            CitizenStatues.LOGGER.info("Web panel access key is stored in {}", configFile.toAbsolutePath());
        } catch (Exception exception) {
            stop();
            CitizenStatues.LOGGER.error("Could not start CitizenStatues web panel. Check config/citizenstatues-web.properties and make sure the port is reserved and not used by Minecraft.", exception);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            try { server.stop(0); } catch (Exception ignored) {}
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        SESSIONS.clear();
        minecraftServer = null;
    }

    private static Properties loadOrCreateConfig(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) { properties.load(input); }
            return properties;
        }
        byte[] key = new byte[36];
        RANDOM.nextBytes(key);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        properties.setProperty("enabled", "true");
        properties.setProperty("bind", "0.0.0.0");
        properties.setProperty("port", "30034");
        properties.setProperty("accessKey", generated);
        properties.setProperty("note", "Use a separate BoxToPlay reserved port, not the Minecraft game port. Keep accessKey private.");
        try (var output = Files.newOutputStream(file)) {
            properties.store(output, "CitizenStatues web administration");
        }
        CitizenStatues.LOGGER.warn("Generated a private CitizenStatues web access key. Find it in {}", file.toAbsolutePath());
        return properties;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                serveIndex(exchange);
                return;
            }
            if (!path.startsWith("/api/")) {
                sendJson(exchange, 404, error("Not found"));
                return;
            }
            if (path.equals("/api/login") && "POST".equals(exchange.getRequestMethod())) {
                login(exchange);
                return;
            }
            if (!authorized(exchange)) {
                sendJson(exchange, 401, error("Session invalide. Reconnecte-toi."));
                return;
            }
            dispatchApi(exchange, path);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, error(exception.getMessage()));
        } catch (Exception exception) {
            CitizenStatues.LOGGER.error("CitizenStatues web request failed", exception);
            sendJson(exchange, 500, error("Erreur interne du panneau."));
        } finally {
            exchange.close();
        }
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Method not allowed"));
            return;
        }
        try (InputStream input = WebAdminServer.class.getResourceAsStream("/assets/citizenstatues/web/index.html")) {
            if (input == null) {
                byte[] fallback = "<h1>CitizenStatues web panel missing index.html</h1>".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(500, fallback.length);
                exchange.getResponseBody().write(fallback);
                return;
            }
            byte[] page = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            exchange.getResponseBody().write(page);
        }
    }

    private static void login(HttpExchange exchange) throws IOException {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        LoginAttempt attempt = LOGIN_ATTEMPTS.get(ip);
        if (attempt != null && attempt.blockedUntil() > now) {
            sendJson(exchange, 429, error("Trop de tentatives. Réessaie dans quelques minutes."));
            return;
        }
        JsonObject body = readJson(exchange);
        String provided = string(body, "accessKey", "");
        if (!constantTimeEquals(accessKey, provided)) {
            int failures = attempt == null ? 1 : attempt.failures() + 1;
            long blockedUntil = failures >= 5 ? now + 5L * 60L * 1000L : 0L;
            LOGIN_ATTEMPTS.put(ip, new LoginAttempt(failures >= 5 ? 0 : failures, blockedUntil));
            sendJson(exchange, 403, error("Clé d’accès incorrecte."));
            return;
        }
        LOGIN_ATTEMPTS.remove(ip);
        String session = UUID.randomUUID().toString();
        SESSIONS.put(session, now + SESSION_LIFETIME_MS);
        JsonObject result = new JsonObject();
        result.addProperty("session", session);
        sendJson(exchange, 200, result);
    }

    private static boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        String session = header.substring(7).trim();
        Long expires = SESSIONS.get(session);
        if (expires == null) return false;
        if (expires < System.currentTimeMillis()) {
            SESSIONS.remove(session);
            return false;
        }
        SESSIONS.put(session, System.currentTimeMillis() + SESSION_LIFETIME_MS);
        return true;
    }

    private static void dispatchApi(HttpExchange exchange, String path) throws Exception {
        if (minecraftServer == null) throw new IllegalStateException("Serveur Minecraft indisponible.");
        CompletableFuture<Void> task = new CompletableFuture<>();
        minecraftServer.execute(() -> {
            try {
                routeApi(exchange, path);
                task.complete(null);
            } catch (Throwable failure) {
                task.completeExceptionally(failure);
            }
        });
        task.get(10, TimeUnit.SECONDS);
    }

    private static void routeApi(HttpExchange exchange, String path) throws Exception {
        var registry = StatueDataService.get().registry();
        String method = exchange.getRequestMethod();

        if (path.equals("/api/state") && "GET".equals(method)) {
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/statues") && "POST".equals(method)) {
            JsonObject body = readJson(exchange);
            String name = required(body, "name", 128);
            boolean hasCoordinates = body.has("x") || body.has("y") || body.has("z");
            if (hasCoordinates && !(body.has("x") && body.has("y") && body.has("z"))) {
                throw new IllegalArgumentException("Renseigne les trois coordonnées X, Y et Z.");
            }
            Double x = hasCoordinates ? number(body, "x", 0) : null;
            Double y = hasCoordinates ? number(body, "y", 64) : null;
            Double z = hasCoordinates ? number(body, "z", 0) : null;
            JsonObject result = createStatue(name, x, y, z);
            sendJson(exchange, 201, result);
            return;
        }
        if (path.equals("/api/groups") && "POST".equals(method)) {
            JsonObject body = readJson(exchange);
            StatueGroup group = registry.createGroup(required(body, "name", 64));
            StatueDataService.get().save();
            sendJson(exchange, 201, object("id", group.id(), "name", group.name()));
            return;
        }

        String[] parts = path.split("/");
        if (parts.length >= 4 && "groups".equals(parts[2])) {
            String groupId = decode(parts[3]);
            StatueGroup group = registry.group(groupId).orElse(null);
            if (group == null) { sendJson(exchange, 404, error("Dossier introuvable.")); return; }

            if (parts.length == 4 && "PATCH".equals(method)) {
                JsonObject body = readJson(exchange);
                if (!registry.renameGroup(groupId, required(body, "name", 64))) {
                    sendJson(exchange, 404, error("Dossier introuvable."));
                    return;
                }
                StatueDataService.get().save();
                sendJson(exchange, 200, ok());
                return;
            }
            if (parts.length == 4 && "DELETE".equals(method)) {
                registry.removeGroup(groupId);
                StatueDataService.get().save();
                sendJson(exchange, 200, ok());
                return;
            }
            if (parts.length == 5 && "members".equals(parts[4]) && "POST".equals(method)) {
                JsonObject body = readJson(exchange);
                String statueId = required(body, "statueId", 128);
                if (!registry.assignStatueToGroup(statueId, groupId)) {
                    sendJson(exchange, 404, error("Statue introuvable."));
                    return;
                }
                StatueDataService.get().save();
                sendJson(exchange, 200, ok());
                return;
            }
            if (parts.length == 6 && "members".equals(parts[4]) && "DELETE".equals(method)) {
                String statueId = decode(parts[5]);
                if (!group.members().contains(statueId)) {
                    sendJson(exchange, 404, error("Cette statue n’est pas dans ce dossier."));
                    return;
                }
                registry.removeStatueFromGroup(statueId);
                StatueDataService.get().save();
                sendJson(exchange, 200, ok());
                return;
            }
            if (parts.length == 5 && "destinations".equals(parts[4]) && "POST".equals(method)) {
                JsonObject body = readJson(exchange);
                String id = optional(body, "id", UUID.randomUUID().toString(), 64);
                String name = required(body, "name", 128);
                String description = optional(body, "description", "", 256);
                String dimension = optional(body, "dimension", "minecraft:overworld", 128);
                double x = number(body, "x", 0);
                double y = number(body, "y", 64);
                double z = number(body, "z", 0);
                Destination destination = new Destination(id, name, description, dimension,
                        x, y, z, 0, 0, "compass", true);
                registry.putGroupDestination(groupId, destination);
                StatueDataService.get().save();
                sendJson(exchange, 201, ok());
                return;
            }
            if (parts.length == 6 && "destinations".equals(parts[4]) && "DELETE".equals(method)) {
                String destinationId = decode(parts[5]);
                if (!registry.removeGroupDestination(groupId, destinationId)) {
                    sendJson(exchange, 404, error("Destination introuvable dans ce dossier."));
                    return;
                }
                StatueDataService.get().save();
                sendJson(exchange, 200, ok());
                return;
            }
        }

        if (parts.length == 5 && "statues".equals(parts[2])
                && "move".equals(parts[4]) && "POST".equals(method)) {
            String statueId = decode(parts[3]);
            JsonObject body = readJson(exchange);
            double x = number(body, "x", 0);
            double y = number(body, "y", 64);
            double z = number(body, "z", 0);
            JsonObject result = moveStatue(statueId, x, y, z);
            sendJson(exchange, 200, result);
            return;
        }
        if (parts.length == 4 && "statues".equals(parts[2]) && "DELETE".equals(method)) {
            String statueId = decode(parts[3]);
            JsonObject result = deleteStatue(statueId);
            sendJson(exchange, 200, result);
            return;
        }
        if (parts.length == 4 && "statues".equals(parts[2]) && "PATCH".equals(method)) {
            String statueId = decode(parts[3]);
            StatueProfile statue = registry.get(statueId).orElse(null);
            if (statue == null) { sendJson(exchange, 404, error("Statue introuvable.")); return; }
            JsonObject body = readJson(exchange);
            if (body.has("name")) statue.rename(required(body, "name", 128));
            if (body.has("enabled")) statue.setEnabled(body.get("enabled").getAsBoolean());
            if (body.has("groupId")) {
                String groupId = body.get("groupId").getAsString();
                if (groupId.isBlank()) registry.removeStatueFromGroup(statueId);
                else if (!registry.assignStatueToGroup(statueId, groupId)) {
                    sendJson(exchange, 404, error("Dossier introuvable."));
                    return;
                }
            }
            StatueDataService.get().save();
            sendJson(exchange, 200, ok());
            return;
        }
        sendJson(exchange, 404, error("Route inconnue."));
    }

    private static JsonObject createStatue(String name, Double requestedX, Double requestedY, Double requestedZ) throws Exception {
        if (minecraftServer == null) throw new IllegalStateException("Serveur Minecraft indisponible.");
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        Runnable operation = () -> {
            try {
                ServerLevel level = minecraftServer.overworld();
                double x;
                double y;
                double z;
                if (requestedX == null) {
                    BlockPos spawn = level.getSharedSpawnPos();
                    x = spawn.getX() + 0.5D;
                    y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            spawn.getX(), spawn.getZ());
                    z = spawn.getZ() + 0.5D;
                } else {
                    x = requestedX;
                    y = requestedY;
                    z = requestedZ;
                }

                GuideStatueEntity entity = ModEntities.GUIDE_STATUE.get().create(level);
                if (entity == null) throw new IllegalStateException("Impossible d'initialiser la statue.");
                var profile = StatueDataService.get().registry().create(name, "general");
                entity.setProfileId(profile.id());
                entity.moveTo(x, y, z, 0.0F, 0.0F);
                if (!level.addFreshEntity(entity)) {
                    StatueDataService.get().registry().remove(profile.id());
                    throw new IllegalStateException(String.format(Locale.ROOT,
                            "Le serveur a refusé la création aux coordonnées X=%.3f, Y=%.3f, Z=%.3f. Vérifie qu'aucune protection ou aucun autre mod ne bloque les entités à cet endroit.",
                            x, y, z));
                }
                StatueDataService.get().save();
                JsonObject result = new JsonObject();
                result.addProperty("ok", true);
                result.addProperty("id", profile.id());
                result.addProperty("message", String.format(Locale.ROOT,
                        "Statue créée en jeu aux coordonnées X=%.3f, Y=%.3f, Z=%.3f.", x, y, z));
                future.complete(result);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        };
        if (minecraftServer.isSameThread()) operation.run(); else minecraftServer.execute(operation);
        return future.get(10, TimeUnit.SECONDS);
    }

    private static JsonObject moveStatue(String statueId, double x, double y, double z) throws Exception {
        if (minecraftServer == null) throw new IllegalStateException("Serveur Minecraft indisponible.");
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        Runnable operation = () -> {
            try {
                if (StatueDataService.get().registry().get(statueId).isEmpty())
                    throw new IllegalArgumentException("Statue introuvable.");
                GuideStatueEntity target = null;
                for (ServerLevel level : minecraftServer.getAllLevels()) {
                    for (var entity : level.getAllEntities()) {
                        if (entity instanceof GuideStatueEntity statue && statueId.equals(statue.getProfileId())) {
                            target = statue;
                            break;
                        }
                    }
                    if (target != null) break;
                }
                if (target == null) throw new IllegalArgumentException("Entité de statue introuvable dans le monde chargé.");
                target.teleportTo(x, y, z);
                StatueDataService.get().save();
                JsonObject result = new JsonObject();
                result.addProperty("ok", true);
                result.addProperty("message", "Statue déplacée.");
                future.complete(result);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        };
        if (minecraftServer.isSameThread()) operation.run(); else minecraftServer.execute(operation);
        return future.get(10, TimeUnit.SECONDS);
    }

    private static JsonObject deleteStatue(String statueId) throws Exception {
        if (minecraftServer == null) throw new IllegalStateException("Serveur Minecraft indisponible.");
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        Runnable operation = () -> {
            try {
                if (!StatueDataService.get().registry().remove(statueId))
                    throw new IllegalArgumentException("Statue introuvable.");
                for (ServerLevel level : minecraftServer.getAllLevels()) {
                    for (var entity : level.getAllEntities()) {
                        if (entity instanceof GuideStatueEntity statue && statueId.equals(statue.getProfileId()))
                            statue.discard();
                    }
                }
                StatueDataService.get().registry().removeStatueFromGroup(statueId);
                StatueDataService.get().save();
                JsonObject result = new JsonObject();
                result.addProperty("ok", true);
                result.addProperty("message", "Statue supprimée.");
                future.complete(result);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        };
        if (minecraftServer.isSameThread()) operation.run(); else minecraftServer.execute(operation);
        return future.get(10, TimeUnit.SECONDS);
    }

    private static JsonObject stateJson() {
        var registry = StatueDataService.get().registry();
        JsonObject root = new JsonObject();
        JsonArray statues = new JsonArray();
        for (StatueProfile profile : registry.all()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", profile.id());
            item.addProperty("name", profile.name());
            item.addProperty("enabled", profile.enabled());
            item.addProperty("specialization", profile.specialization());
            registry.groupForStatue(profile.id()).ifPresent(group -> item.addProperty("groupId", group.id()));
            JsonArray destinations = new JsonArray();
            profile.destinations().values().stream()
                    .sorted(java.util.Comparator.comparing(Destination::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(d -> destinations.add(destinationJson(d)));
            item.add("destinations", destinations);
            statues.add(item);
        }
        root.add("statues", statues);

        JsonArray groups = new JsonArray();
        for (StatueGroup group : registry.groups()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", group.id());
            item.addProperty("name", group.name());
            JsonArray members = new JsonArray();
            group.members().forEach(members::add);
            item.add("members", members);
            JsonArray destinations = new JsonArray();
            registry.groupDestinations(group.id()).values().stream()
                    .sorted(java.util.Comparator.comparing(Destination::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(d -> destinations.add(destinationJson(d)));
            item.add("destinations", destinations);
            groups.add(item);
        }
        root.add("groups", groups);
        return root;
    }

    private static JsonObject destinationJson(Destination d) {
        JsonObject item = new JsonObject();
        item.addProperty("id", d.id());
        item.addProperty("name", d.name());
        item.addProperty("description", d.description());
        item.addProperty("dimension", d.dimension());
        item.addProperty("x", d.x());
        item.addProperty("y", d.y());
        item.addProperty("z", d.z());
        item.addProperty("enabled", d.enabled());
        return item;
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IllegalArgumentException("Requête trop volumineuse.");
        if (bytes.length == 0) return new JsonObject();
        JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("JSON invalide.");
        return parsed.getAsJsonObject();
    }

    private static String required(JsonObject object, String key, int max) {
        String value = string(object, key, "").trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException("Champ invalide : " + key);
        return value;
    }

    private static String optional(JsonObject object, String key, String fallback, int max) {
        String value = string(object, key, fallback).trim();
        if (value.length() > max) throw new IllegalArgumentException("Champ trop long : " + key);
        return value;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static double number(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return fallback;
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000) throw new IllegalArgumentException("Coordonnée invalide : " + key);
        return value;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return expected != null && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject object(String key, String value, String key2, String value2) {
        JsonObject result = new JsonObject();
        result.addProperty(key, value);
        result.addProperty(key2, value2);
        return result;
    }

    private static JsonObject ok() { return object("ok", "true", "status", "saved"); }
    private static JsonObject error(String message) { return object("error", message == null ? "Erreur" : message, "ok", "false"); }

    private static void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}