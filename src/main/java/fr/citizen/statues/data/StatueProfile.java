package fr.citizen.statues.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Independent configuration and destination set for one statue. */
public final class StatueProfile {
    private final String id;
    private String name;
    private String specialization;
    private boolean enabled;
    private final Map<String, Destination> destinations;
    private final Map<String, String> synonyms;

    public StatueProfile(String id, String name, String specialization, boolean enabled,
                         Map<String, Destination> destinations, Map<String, String> synonyms) {
        this.id = require(id, "id");
        this.name = require(name, "name");
        this.specialization = Objects.requireNonNullElse(specialization, "general");
        this.enabled = enabled;
        this.destinations = new LinkedHashMap<>(destinations == null ? Map.of() : destinations);
        this.synonyms = new LinkedHashMap<>(synonyms == null ? Map.of() : synonyms);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String specialization() { return specialization; }
    public boolean enabled() { return enabled; }
    public Map<String, Destination> destinations() { return Map.copyOf(destinations); }
    public Map<String, String> synonyms() { return Map.copyOf(synonyms); }

    public void rename(String value) { name = require(value, "name"); }
    public void setSpecialization(String value) { specialization = require(value, "specialization"); }
    public void setEnabled(boolean value) { enabled = value; }

    public void putDestination(Destination destination) {
        destinations.put(destination.id(), destination);
    }
    public boolean removeDestination(String id) { return destinations.remove(id) != null; }
    public void putSynonym(String phrase, String destinationId) {
        synonyms.put(require(phrase, "phrase").toLowerCase(java.util.Locale.ROOT),
                require(destinationId, "destinationId"));
    }
    public void removeSynonym(String phrase) {
        if (phrase != null) synonyms.remove(phrase.toLowerCase(java.util.Locale.ROOT));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException("Invalid " + field);
        return value.trim();
    }
}
