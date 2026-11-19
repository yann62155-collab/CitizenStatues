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

    public static Result createCatalogDestinationHere(ServerPlayer actor, String id, String name) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        try {
            var pos = actor.position();
            var destination = new Destination(id, name, "", actor.level().dimension().location().toString(),
                    pos.x, pos.y, pos.z, actor.getYRot(), actor.getXRot(), "compass", true);
            StatueDataService.get().registry().putCatalogDestination(destination);
            StatueDataService.get().save();
            return Result.SUCCESS;
        } catch (IllegalArgumentException ex) { return Result.INVALID; }
    }

    public static Result putCatalogDestination(ServerPlayer actor, Destination destination) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        try {
            StatueDataService.get().registry().putCatalogDestination(destination);
            StatueDataService.get().save();
            return Result.SUCCESS;
        } catch (IllegalArgumentException ex) {
            return Result.INVALID;
        }
    }

    public static Result setCatalogDestinationEnabled(ServerPlayer actor, String destinationId,
                                                       boolean enabled) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        Destination current = StatueDataService.get().registry().destination(destinationId).orElse(null);
        if (current == null) return Result.NOT_FOUND;
        Destination updated = new Destination(current.id(), current.name(), current.description(),
                current.dimension(), current.x(), current.y(), current.z(), current.yaw(), current.pitch(),
                current.icon(), enabled);
        StatueDataService.get().registry().putCatalogDestination(updated);
        StatueDataService.get().save();
        return Result.SUCCESS;
    }

    public static Result assignCatalogDestination(ServerPlayer actor, String statueId,
                                                  String destinationId) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        if (StatueDataService.get().registry().get(statueId).isEmpty()
                || StatueDataService.get().registry().destination(destinationId).isEmpty()) {
            return Result.NOT_FOUND;
        }
        if (!StatueDataService.get().registry().assignDestination(statueId, destinationId)) {
            return Result.INVALID;
        }
        StatueDataService.get().save();
        return Result.SUCCESS;
    }

    public static Result unassignCatalogDestination(ServerPlayer actor, String statueId,
                                                    String destinationId) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        if (StatueDataService.get().registry().get(statueId).isEmpty()) return Result.NOT_FOUND;
        if (!StatueDataService.get().registry().unassignDestination(statueId, destinationId)) {
            return Result.NOT_FOUND;
        }
        StatueDataService.get().save();
        return Result.SUCCESS;
    }

    public static Result removeDestination(ServerPlayer actor, String statueId, String destinationId) throws IOException {
        if (!AdminPermission.allowed(actor)) return Result.DENIED;
        StatueProfile profile = StatueDataService.get().registry().get(statueId).orElse(null);
        if (profile == null) return Result.NOT_FOUND;
        if (!profile.removeDestination(destinationId)) return Result.NOT_FOUND;
        StatueDataService.get().save(); return Result.SUCCESS;
    }
}
