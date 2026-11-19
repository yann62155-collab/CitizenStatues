package fr.citizen.statues.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory registry. Mutations are explicit so the persistence layer can save atomically. */
public final class StatueRegistry {
    private final Map<String, StatueProfile> statues = new LinkedHashMap<>();

    public synchronized StatueProfile create(String name, String specialization) {
        String id = UUID.randomUUID().toString();
        StatueProfile profile = new StatueProfile(id, name, specialization, true, Map.of(), Map.of());
        statues.put(id, profile);
        return profile;
    }

    public synchronized Optional<StatueProfile> get(String id) {
        return Optional.ofNullable(statues.get(id));
    }

    public synchronized Collection<StatueProfile> all() {
        return java.util.List.copyOf(statues.values());
    }

    public synchronized boolean remove(String id) {
        return statues.remove(id) != null;
    }

    public synchronized void put(StatueProfile profile) {
        statues.put(profile.id(), profile);
    }

    public synchronized int size() { return statues.size(); }
}
