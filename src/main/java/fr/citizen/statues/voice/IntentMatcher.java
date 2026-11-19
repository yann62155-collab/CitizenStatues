package fr.citizen.statues.voice;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueProfile;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/** Conservative French destination matcher. Unknown speech never becomes coordinates or commands. */
public final class IntentMatcher {
    private IntentMatcher() {}

    public static Optional<Destination> match(String utterance, StatueProfile profile) {
        String input = normalize(utterance);
        if (input.isBlank() || !profile.enabled()) return Optional.empty();

        for (var entry : profile.synonyms().entrySet()) {
            if (input.contains(normalize(entry.getKey()))) {
                Destination d = profile.destinations().get(entry.getValue());
                if (d != null && d.enabled()) return Optional.of(d);
            }
        }

        return profile.destinations().values().stream()
                .filter(Destination::enabled)
                .filter(d -> input.contains(normalize(d.name())))
                .max(Comparator.comparingInt(d -> normalize(d.name()).length()));
    }

    static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
