package fr.citizen.statues.admin;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.data.StatueProfile;
import fr.citizen.statues.security.AdminPermission;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Optional;

/** Server-side mutation facade. Every operation checks operator permission before changing data. */
public final class StatueAdminService {
    public enum Result { SUCCESS, DENIED, NOT_FOUND, INVALID }
    private StatueAdminService() {}

    public static Result rename(ServerPlayer actor, String statueId, String name) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        StatueProfile profile = StatueDataService.get().registry().get(statueId).orElse(null);
        if (profile == null) return Result.NOT_FOUND;
        try { profile.rename(name); StatueDataService.get().save(); return Result.SUCCESS; }
        catch (IllegalArgumentException ex) { return Result.INVALID; }
    }

    public static Result setEnabled(ServerPlayer actor, String statueId, boolean enabled) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        StatueProfile profile = StatueDataService.get().registry().get(statueId).orElse(null);
        if (profile == null) return Result.NOT_FOUND;
        profile.setEnabled(enabled); StatueDataService.get().save(); return Result.SUCCESS;
    }

    public static Result remove(ServerPlayer actor, String statueId) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        if (!StatueDataService.get().registry().remove(statueId)) return Result.NOT_FOUND;
        StatueDataService.get().save(); return Result.SUCCESS;
    }

    public static Result putDestination(ServerPlayer actor, String statueId, Destination destination) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        StatueProfile profile = StatueDataService.get().registry().get(statueId).orElse(null);
        if (profile == null) return Result.NOT_FOUND;
        profile.putDestination(destination); StatueDataService.get().save(); return Result.SUCCESS;
    }

    public static Result removeDestination(ServerPlayer actor, String statueId, String destinationId) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        StatueProfile profile = StatueDataService.get().registry().get(statueId).orElse(null);
        if (profile == null) return Result.NOT_FOUND;
        if (!profile.removeDestination(destinationId)) return Result.NOT_FOUND;
        StatueDataService.get().save(); return Result.SUCCESS;
    }
}
