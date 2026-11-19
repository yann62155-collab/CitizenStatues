package fr.citizen.statues.data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** A folder of guide statues that share the same destination set. */
public final class StatueGroup {
    private final String id;
    private String name;
    private final LinkedHashSet<String> members = new LinkedHashSet<>();

    public StatueGroup(String id, String name, List<String> members) {
        this.id = require(id, "id", 64);
        this.name = require(name, "name", 64);
        if (members != null) this.members.addAll(members);
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<String> members() { return List.copyOf(members); }

    public void rename(String value) { name = require(value, "name", 64); }
    public boolean addMember(String statueId) { return members.add(require(statueId, "statueId", 128)); }
    public boolean removeMember(String statueId) { return members.remove(statueId); }

    private static String require(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > max) throw new IllegalArgumentException("Invalid " + field);
        return clean;
    }
}