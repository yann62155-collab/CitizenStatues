package fr.citizen.statues.security;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight per-player cooldown. Time is supplied by the caller for testability. */
public final class PlayerRateLimiter {
    private final ConcurrentHashMap<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public PlayerRateLimiter(long cooldownMillis) {
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis cannot be negative");
        this.cooldownMillis = cooldownMillis;
    }

    public boolean tryAcquire(UUID player, long nowMillis) {
        final boolean[] acquired = {false};
        lastUse.compute(player, (id, previous) -> {
            if (previous == null || nowMillis - previous >= cooldownMillis) {
                acquired[0] = true;
                return nowMillis;
            }
            return previous;
        });
        return acquired[0];
    }

    public void forget(UUID player) { lastUse.remove(player); }
}
