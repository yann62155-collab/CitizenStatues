package fr.citizen.statues.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory registry. Mutations are explicit so the persistence layer can save atomically. */
public final class StatueRegistry {
    private final Map<String, StatueProfile> statues = new LinkedHashMap<>();
    private final Map<String, Destination> catalog = new LinkedHashMap<>();

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

    public synchronized Collection<Destination> catalog() { return java.util.List.copyOf(catalog.values()); }
    public synchronized Optional<Destination> destination(String id) { return Optional.ofNullable(catalog.get(id)); }
    public synchronized void putCatalogDestination(Destination destination) {
        catalog.put(destination.id(), destination);
        for (StatueProfile profile : statues.values()) {
            if (profile.destinations().containsKey(destination.id())) profile.putDestination(destination);
        }
    }
    public synchronized boolean removeCatalogDestination(String id) { return catalog.remove(id) != null; }
    public synchronized boolean assignDestination(String statueId, String destinationId) {
        StatueProfile profile = statues.get(statueId);
        Destination destination = catalog.get(destinationId);
        if (profile == null || destination == null) return false;
        profile.putDestination(destination);
        return true;
    }
    public synchronized boolean unassignDestination(String statueId, String destinationId) {
        StatueProfile profile = statues.get(statueId);
        return profile != null && profile.removeDestination(destinationId);
    }
    public synchronized void replaceCatalog(Map<String, Destination> destinations) {
        catalog.clear();
        if (destinations != null) catalog.putAll(destinations);
    }
}
