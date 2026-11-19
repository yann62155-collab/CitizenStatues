package fr.citizen.statues.voice;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player misunderstanding counter; callers decide when to show the fallback GUI. */
public final class RetrySession {
    public enum Outcome { ASK_REPEAT, SHOW_EXAMPLES, OPEN_DESTINATION_MENU, UNDERSTOOD }
    private final ConcurrentHashMap<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final int maximumAttempts;

    public RetrySession(int maximumAttempts) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        this.maximumAttempts = maximumAttempts;
    }

    public Outcome misunderstood(UUID player) {
        int count = attempts.merge(player, 1, Integer::sum);
        if (count >= maximumAttempts) {
            attempts.remove(player);
            return Outcome.OPEN_DESTINATION_MENU;
        }
        return count == 1 ? Outcome.ASK_REPEAT : Outcome.SHOW_EXAMPLES;
    }

    public void understood(UUID player) { attempts.remove(player); }
    public void closed(UUID player) { attempts.remove(player); }
    public int attempts(UUID player) { return attempts.getOrDefault(player, 0); }
}
