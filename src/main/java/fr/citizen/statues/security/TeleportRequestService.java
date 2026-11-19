package fr.citizen.statues.security;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.data.StatueProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validates a player's requested destination against the profile bound to the clicked statue. */
public final class TeleportRequestService {
    public enum Result { TELEPORTED, UNKNOWN_STATUE, DISABLED_STATUE, UNKNOWN_DESTINATION, COOLDOWN, DENIED }

    private static final PlayerRateLimiter LIMITER = new PlayerRateLimiter(1_500);
    private TeleportRequestService() {}

    public static Result request(ServerPlayer player, MinecraftServer server,
                                 String statueProfileId, String destinationId, long nowMillis) {
        if (!LIMITER.tryAcquire(player.getUUID(), nowMillis)) return Result.COOLDOWN;
        StatueProfile profile = StatueDataService.get().registry().get(statueProfileId).orElse(null);
        if (profile == null) return Result.UNKNOWN_STATUE;
        if (!profile.enabled()) return Result.DISABLED_STATUE;
        Destination destination = profile.destinations().get(destinationId);
        if (destination == null || !destination.enabled()) return Result.UNKNOWN_DESTINATION;
        TeleportService.Status status = TeleportService.teleport(player, server, destination);
        return status == TeleportService.Status.SUCCESS ? Result.TELEPORTED : Result.DENIED;
    }

    public static void clear(UUID playerId) { LIMITER.forget(playerId); }
}
