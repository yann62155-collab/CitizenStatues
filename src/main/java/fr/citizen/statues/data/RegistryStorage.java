package fr.citizen.statues.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned JSON persistence with atomic replacement and corrupt-file backup. */
public final class RegistryStorage {
    private static final int FORMAT_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RegistryStorage() {}

    public static void save(Path file, StatueRegistry registry) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        JsonArray catalog = new JsonArray();
        for (Destination d : registry.catalog()) catalog.add(writeDestination(d));
        root.add("destinationCatalog", catalog);
        JsonArray statues = new JsonArray();
        for (StatueProfile profile : registry.all()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", profile.id());
            p.addProperty("name", profile.name());
            p.addProperty("specialization", profile.specialization());
            p.addProperty("enabled", profile.enabled());
            JsonArray destinations = new JsonArray();
            for (Destination d : profile.destinations().values()) {
                destinations.add(writeDestination(d));
            }
            p.add("destinations", destinations);
            JsonObject synonyms = new JsonObject();
            profile.synonyms().forEach(synonyms::addProperty);
            p.add("synonyms", synonyms);
            statues.add(p);
        }
        root.add("statues", statues);
        JsonArray groups = new JsonArray();
        for (StatueGroup group : registry.groups()) {
            JsonObject g = new JsonObject();
            g.addProperty("id", group.id());
            g.addProperty("name", group.name());
            JsonArray members = new JsonArray();
            group.members().forEach(members::add);
            g.add("members", members);
            groups.add(g);
        }
        root.add("groups", groups);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static StatueRegistry load(Path file) throws IOException {
        StatueRegistry registry = new StatueRegistry();
        if (!Files.exists(file)) return registry;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            int version = root.get("formatVersion").getAsInt();
            if (version < 1 || version > FORMAT_VERSION) throw new IOException("Unsupported registry format: " + version);
            Map<String, Destination> global = new LinkedHashMap<>();
            JsonArray globalArray = root.getAsJsonArray("destinationCatalog");
            if (globalArray != null) for (JsonElement item : globalArray) {
                Destination destination = readDestination(item.getAsJsonObject());
                global.put(destination.id(), destination);
            }
            for (JsonElement element : root.getAsJsonArray("statues")) {
                JsonObject p = element.getAsJsonObject();
                Map<String, Destination> destinations = new LinkedHashMap<>();
                JsonArray array = p.getAsJsonArray("destinations");
                if (array != null) for (JsonElement dElement : array) {
                    Destination dValue = readDestination(dElement.getAsJsonObject());
                    global.putIfAbsent(dValue.id(), dValue);
                    destinations.put(dValue.id(), dValue);
                }
                Map<String, String> synonyms = new LinkedHashMap<>();
                JsonObject syn = p.getAsJsonObject("synonyms");
                if (syn != null) for (String key : syn.keySet()) synonyms.put(key, syn.get(key).getAsString());
                StatueProfile profile = new StatueProfile(p.get("id").getAsString(),
                        p.get("name").getAsString(), string(p, "specialization", "general"),
                        bool(p, "enabled", true), destinations, synonyms);
                registry.put(profile);
            }
            registry.replaceCatalog(global);
            JsonArray groupArray = root.getAsJsonArray("groups");
            if (groupArray != null) for (JsonElement item : groupArray) {
                JsonObject group = item.getAsJsonObject();
                java.util.List<String> members = new java.util.ArrayList<>();
                JsonArray memberArray = group.getAsJsonArray("members");
                if (memberArray != null) for (JsonElement member : memberArray) {
                    members.add(member.getAsString());
                }
                registry.restoreGroup(group.get("id").getAsString(),
                        group.get("name").getAsString(), members);
            }
            return registry;
        } catch (RuntimeException invalid) {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return registry;
        }
    }

    private static JsonObject writeDestination(Destination d) {
        JsonObject item = new JsonObject();
        item.addProperty("id", d.id()); item.addProperty("name", d.name());
        item.addProperty("description", d.description()); item.addProperty("dimension", d.dimension());
        item.addProperty("x", d.x()); item.addProperty("y", d.y()); item.addProperty("z", d.z());
        item.addProperty("yaw", d.yaw()); item.addProperty("pitch", d.pitch());
        item.addProperty("icon", d.icon()); item.addProperty("enabled", d.enabled());
        return item;
    }

    private static Destination readDestination(JsonObject d) {
        return new Destination(d.get("id").getAsString(), d.get("name").getAsString(),
                string(d, "description", ""), d.get("dimension").getAsString(),
                d.get("x").getAsDouble(), d.get("y").getAsDouble(), d.get("z").getAsDouble(),
                number(d, "yaw", 0).floatValue(), number(d, "pitch", 0).floatValue(),
                string(d, "icon", "compass"), bool(d, "enabled", true));
    }

    private static String string(JsonObject o, String k, String fallback) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : fallback;
    }
    private static Number number(JsonObject o, String k, Number fallback) {
        return o.has(k) ? o.get(k).getAsNumber() : fallback;
    }
    private static boolean bool(JsonObject o, String k, boolean fallback) {
        return o.has(k) ? o.get(k).getAsBoolean() : fallback;
    }
}
