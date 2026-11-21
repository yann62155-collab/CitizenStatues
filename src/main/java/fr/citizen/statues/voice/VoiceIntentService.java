package fr.citizen.statues.voice;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueProfile;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player text intent orchestration; the recognizer supplies transient text only. */
public final class VoiceIntentService {
    public record Decision(boolean understood, Destination destination,
                           RetrySession.Outcome outcome, String response, IntentMatcher.Command command) {}

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
        return accept(player, List.of(recognizedText == null ? "" : recognizedText), profile);
    }

    public Decision accept(UUID player, List<String> hypotheses, StatueProfile profile) {
        String expected = activeProfiles.get(player);
        if (expected == null || !expected.equals(profile.id())) {
            return new Decision(false, null, RetrySession.Outcome.ASK_REPEAT,
                    "Cette interaction n'est plus active. Clique à nouveau sur la statue.", IntentMatcher.Command.NONE);
        }
        IntentMatcher.Result result = IntentMatcher.analyze(hypotheses, profile);
        if (result.command() == IntentMatcher.Command.CANCEL) {
            close(player);
            return new Decision(false, null, RetrySession.Outcome.ASK_REPEAT, "D'accord. Bonne route !", result.command());
        }
        if (result.command() == IntentMatcher.Command.MENU) {
            retries.closed(player);
            return new Decision(false, null, RetrySession.Outcome.OPEN_DESTINATION_MENU,
                    ConversationPrompts.forOutcome(RetrySession.Outcome.OPEN_DESTINATION_MENU), result.command());
        }
        if (result.understood()) {
            retries.understood(player);
            return new Decision(true, result.destination(), RetrySession.Outcome.UNDERSTOOD,
                    ConversationPrompts.forOutcome(RetrySession.Outcome.UNDERSTOOD), result.command());
        }
        RetrySession.Outcome outcome = retries.misunderstood(player);
        String response = outcome == RetrySession.Outcome.OPEN_DESTINATION_MENU
                ? ConversationPrompts.forOutcome(outcome)
                : ConversationPrompts.forMisunderstanding(result, profile.destinations().values().stream()
                        .filter(Destination::enabled)
                        .sorted(Comparator.comparing(Destination::name, String.CASE_INSENSITIVE_ORDER)).toList());
        return new Decision(false, null, outcome, response, result.command());
    }

    public void close(UUID player) {
        activeProfiles.remove(player);
        retries.closed(player);
    }
}
