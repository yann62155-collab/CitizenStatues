package fr.citizen.statues.voice;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side capture session policy. It stores no audio and enforces a hard duration cap. */
public final class VoiceCapturePolicy {
    public enum State { IDLE, CAPTURING, COMPLETE, CANCELLED, EXPIRED }
    private final ConcurrentHashMap<UUID, Long> starts = new ConcurrentHashMap<>();
    private final int maxSeconds;

    public VoiceCapturePolicy(int maxSeconds) {
        if (maxSeconds < 1 || maxSeconds > 30) throw new IllegalArgumentException("maxSeconds must be 1..30");
        this.maxSeconds = maxSeconds;
    }

    public State begin(UUID player, long nowMillis) {
        if (starts.putIfAbsent(player, nowMillis) != null) return State.CAPTURING;
        return State.CAPTURING;
    }

    public State poll(UUID player, long nowMillis) {
        Long start = starts.get(player);
        if (start == null) return State.IDLE;
        if (nowMillis - start >= maxSeconds * 1000L) {
            starts.remove(player);
            return State.EXPIRED;
        }
        return State.CAPTURING;
    }

    public State complete(UUID player) {
        return starts.remove(player) == null ? State.IDLE : State.COMPLETE;
    }

    public State cancel(UUID player) {
        return starts.remove(player) == null ? State.IDLE : State.CANCELLED;
    }
}
