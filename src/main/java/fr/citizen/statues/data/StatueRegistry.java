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
    private final Map<String, StatueGroup> groups = new LinkedHashMap<>();

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
        boolean removed = statues.remove(id) != null;
        if (removed) removeStatueFromGroup(id);
        return removed;
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
        // A catalog destination already used by one group member is shared by every member.
        for (StatueGroup group : groups.values()) {
            boolean used = group.members().stream().map(statues::get).filter(java.util.Objects::nonNull)
                    .anyMatch(profile -> profile.destinations().containsKey(destination.id()));
            if (used) for (String member : group.members()) {
                StatueProfile profile = statues.get(member);
                if (profile != null) profile.putDestination(destination);
            }
        }
    }
    public synchronized boolean removeCatalogDestination(String id) { return catalog.remove(id) != null; }
    public synchronized boolean assignDestination(String statueId, String destinationId) {
        StatueProfile profile = statues.get(statueId);
        Destination destination = catalog.get(destinationId);
        if (profile == null || destination == null) return false;
        putDestinationForStatue(statueId, destination);
        return true;
    }
    public synchronized boolean unassignDestination(String statueId, String destinationId) {
        StatueProfile profile = statues.get(statueId);
        if (profile == null) return false;
        StatueGroup group = groupForStatueInternal(statueId);
        if (group == null) return profile.removeDestination(destinationId);
        boolean removed = false;
        for (String member : group.members()) {
            StatueProfile grouped = statues.get(member);
            if (grouped != null) removed |= grouped.removeDestination(destinationId);
        }
        return removed;
    }

    /** Adds or updates a destination on one statue and propagates it to all its group members. */
    public synchronized void putDestinationForStatue(String statueId, Destination destination) {
        StatueProfile profile = statues.get(statueId);
        if (profile == null) throw new IllegalArgumentException("Unknown statue: " + statueId);
        StatueGroup group = groupForStatueInternal(statueId);
        if (group == null) {
            profile.putDestination(destination);
            return;
        }
        for (String member : group.members()) {
            StatueProfile grouped = statues.get(member);
            if (grouped != null) grouped.putDestination(destination);
        }
    }

    public synchronized boolean removeDestinationForStatue(String statueId, String destinationId) {
        StatueProfile profile = statues.get(statueId);
        if (profile == null) return false;
        StatueGroup group = groupForStatueInternal(statueId);
        if (group == null) return profile.removeDestination(destinationId);
        boolean removed = false;
        for (String member : group.members()) {
            StatueProfile grouped = statues.get(member);
            if (grouped != null) removed |= grouped.removeDestination(destinationId);
        }
        return removed;
    }

    public synchronized StatueGroup createGroup(String name) {
        String id = UUID.randomUUID().toString();
        StatueGroup group = new StatueGroup(id, name, java.util.List.of());
        groups.put(id, group);
        return group;
    }

    public synchronized Collection<StatueGroup> groups() {
        return java.util.List.copyOf(groups.values());
    }

    public synchronized Optional<StatueGroup> group(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    public synchronized boolean renameGroup(String id, String name) {
        StatueGroup group = groups.get(id);
        if (group == null) return false;
        group.rename(name);
        return true;
    }

    public synchronized boolean removeGroup(String id) {
        return groups.remove(id) != null;
    }

    public synchronized boolean assignStatueToGroup(String statueId, String groupId) {
        StatueProfile statue = statues.get(statueId);
        StatueGroup target = groups.get(groupId);
        if (statue == null || target == null) return false;
        for (StatueGroup group : groups.values()) group.removeMember(statueId);
        target.addMember(statueId);

        // Merge existing destination knowledge once, then keep the group synchronized.
        Map<String, Destination> shared = new LinkedHashMap<>();
        for (String member : target.members()) {
            StatueProfile grouped = statues.get(member);
            if (grouped != null) shared.putAll(grouped.destinations());
        }
        for (String member : target.members()) {
            StatueProfile grouped = statues.get(member);
            if (grouped != null) shared.values().forEach(grouped::putDestination);
        }
        return true;
    }

    public synchronized boolean removeStatueFromGroup(String statueId) {
        boolean removed = false;
        for (StatueGroup group : groups.values()) removed |= group.removeMember(statueId);
        return removed;
    }

    public synchronized Optional<StatueGroup> groupForStatue(String statueId) {
        return Optional.ofNullable(groupForStatueInternal(statueId));
    }

    public synchronized void putGroupDestination(String groupId, Destination destination) {
        StatueGroup group = groups.get(groupId);
        if (group == null) throw new IllegalArgumentException("Unknown group: " + groupId);
        catalog.put(destination.id(), destination);
        for (String member : group.members()) {
            StatueProfile profile = statues.get(member);
            if (profile != null) profile.putDestination(destination);
        }
    }

    public synchronized boolean removeGroupDestination(String groupId, String destinationId) {
        StatueGroup group = groups.get(groupId);
        if (group == null) return false;
        boolean removed = false;
        for (String member : group.members()) {
            StatueProfile profile = statues.get(member);
            if (profile != null) removed |= profile.removeDestination(destinationId);
        }
        return removed;
    }

    public synchronized Map<String, Destination> groupDestinations(String groupId) {
        StatueGroup group = groups.get(groupId);
        if (group == null) return Map.of();
        Map<String, Destination> shared = new LinkedHashMap<>();
        for (String member : group.members()) {
            StatueProfile profile = statues.get(member);
            if (profile != null) shared.putAll(profile.destinations());
        }
        return Map.copyOf(shared);
    }

    private StatueGroup groupForStatueInternal(String statueId) {
        return groups.values().stream().filter(group -> group.members().contains(statueId))
                .findFirst().orElse(null);
    }

    /** Used by persistence when restoring a previously saved group. */
    public synchronized void restoreGroup(String id, String name, java.util.List<String> members) {
        java.util.List<String> valid = members == null ? java.util.List.of()
                : members.stream().filter(statues::containsKey).distinct().toList();
        StatueGroup group = new StatueGroup(id, name, valid);
        for (String member : valid) removeStatueFromGroup(member);
        groups.put(id, group);
        Map<String, Destination> shared = new LinkedHashMap<>();
        for (String member : group.members()) {
            StatueProfile profile = statues.get(member);
            if (profile != null) shared.putAll(profile.destinations());
        }
        for (String member : group.members()) {
            StatueProfile profile = statues.get(member);
            if (profile != null) shared.values().forEach(profile::putDestination);
        }
    }
    public synchronized void replaceCatalog(Map<String, Destination> destinations) {
        catalog.clear();
        if (destinations != null) catalog.putAll(destinations);
    }
}
