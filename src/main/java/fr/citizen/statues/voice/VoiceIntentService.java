package fr.citizen.statues.voice;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player text intent orchestration; the recognizer supplies transient text only. */
public final class VoiceIntentService {
    public record Decision(boolean understood, Destination destination,
                           RetrySession.Outcome outcome, String response) {}

    private final RetrySession retries;
    private final ConcurrentHashMap<UUID, String> activeProfiles = new ConcurrentHashMap<>();

    public VoiceIntentService(int maximumAttempts) {
        retries = new RetrySession(maximumAttempts);
    }

    public void begin(UUID player, String profileId) {
        activeProfiles.put(player, profileId);
        retries.closed(player);
    }

    public Decision accept(UUID player, String recognizedText, StatueProfile profile) {
        String expected = activeProfiles.get(player);
        if (expected == null || !expected.equals(profile.id())) {
            return new Decision(false, null, RetrySession.Outcome.ASK_REPEAT,
                    "Cette interaction n'est plus active. Clique à nouveau sur la statue.");
        }
        Optional<Destination> destination = IntentMatcher.match(recognizedText, profile);
        if (destination.isPresent()) {
            retries.understood(player);
            return new Decision(true, destination.get(), RetrySession.Outcome.UNDERSTOOD,
                    ConversationPrompts.forOutcome(RetrySession.Outcome.UNDERSTOOD));
        }
        RetrySession.Outcome outcome = retries.misunderstood(player);
        return new Decision(false, null, outcome, ConversationPrompts.forOutcome(outcome));
    }

    public void close(UUID player) {
        activeProfiles.remove(player);
        retries.closed(player);
    }
}
