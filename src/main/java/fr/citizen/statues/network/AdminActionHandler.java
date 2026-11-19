package fr.citizen.statues.network;

import fr.citizen.statues.admin.StatueAdminService;
import fr.citizen.statues.data.StatueDataService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-only execution of admin screen actions. Permission checks are repeated on every request. */
public final class AdminActionHandler {
    private AdminActionHandler() {}

    public static void handle(AdminActionPayload payload, net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer actor) || !actor.hasPermissions(2)) {
            if (player != null) player.sendSystemMessage(Component.literal("Accès administrateur refusé."));
            return;
        }
        try {
            StatueAdminService.Result result = switch (payload.action()) {
                case "refresh" -> StatueAdminService.Result.SUCCESS;
                case "statue-create-here" -> createStatueHere(actor, payload.statueId());
                case "catalog-create-here" -> StatueAdminService.createCatalogDestinationHere(actor,
                        payload.destinationId(), payload.statueId());
                case "catalog-rename" -> StatueAdminService.renameCatalogDestination(actor,
                        payload.destinationId(), payload.statueId());
                case "catalog-move-here" -> StatueAdminService.moveCatalogDestinationHere(actor,
                        payload.destinationId());
                case "catalog-description" -> StatueAdminService.describeCatalogDestination(actor,
                        payload.destinationId(), payload.statueId());
                case "catalog-remove" -> StatueAdminService.removeCatalogDestination(actor, payload.destinationId());
                case "statue-remove" -> StatueAdminService.remove(actor, payload.statueId());
                case "statue-rename" -> StatueAdminService.rename(actor, payload.statueId(), payload.destinationId());
                case "statue-enable" -> StatueAdminService.setEnabled(actor, payload.statueId(), true);
                case "statue-disable" -> StatueAdminService.setEnabled(actor, payload.statueId(), false);
                case "destination-assign" -> StatueAdminService.assignCatalogDestination(actor,
                        payload.statueId(), payload.destinationId());
                case "destination-unassign" -> StatueAdminService.unassignCatalogDestination(actor,
                        payload.statueId(), payload.destinationId());
                case "destination-enable" -> StatueAdminService.setCatalogDestinationEnabled(actor,
                        payload.destinationId(), true);
                case "destination-disable" -> StatueAdminService.setCatalogDestinationEnabled(actor,
                        payload.destinationId(), false);
                default -> StatueAdminService.Result.INVALID;
            };
            actor.sendSystemMessage(Component.literal(switch (result) {
                case SUCCESS -> "Modification enregistrée.";
                case DENIED -> "Accès administrateur refusé.";
                case NOT_FOUND -> "Statue ou destination introuvable.";
                case INVALID -> "Action invalide.";
            }));
            if (result == StatueAdminService.Result.SUCCESS) sendSnapshot(actor);
        } catch (Exception exception) {
            actor.sendSystemMessage(Component.literal("Échec de sauvegarde : " + exception.getMessage()));
        }
    }


    private static StatueAdminService.Result createStatueHere(ServerPlayer actor, String name) throws Exception {
        if (name == null || name.isBlank()) {
            actor.sendSystemMessage(Component.literal("Saisis un nom dans le champ « Nom affiché » avant de créer la statue."));
            return StatueAdminService.Result.INVALID;
        }
        String cleanName = name.trim();
        if (cleanName.length() > 64) {
            actor.sendSystemMessage(Component.literal("Le nom de la statue doit faire 64 caractères maximum."));
            return StatueAdminService.Result.INVALID;
        }

        var level = actor.serverLevel();
        var entity = fr.citizen.statues.registry.ModEntities.GUIDE_STATUE.get().create(level);
        if (entity == null) {
            actor.sendSystemMessage(Component.literal("Impossible d'initialiser l'entité Guide Statue. Vérifie l'enregistrement du mod."));
            return StatueAdminService.Result.INVALID;
        }

        var profile = StatueDataService.get().registry().create(cleanName, "general");
        entity.setProfileId(profile.id());

        // Place the statue just in front of the operator to avoid spawning it inside the player hitbox.
        double radians = Math.toRadians(actor.getYRot());
        double x = actor.getX() - Math.sin(radians) * 1.25D;
        double z = actor.getZ() + Math.cos(radians) * 1.25D;
        entity.moveTo(x, actor.getY(), z, actor.getYRot(), 0.0F);

        if (!level.addFreshEntity(entity)) {
            StatueDataService.get().registry().remove(profile.id());
            actor.sendSystemMessage(Component.literal("Le serveur a refusé le spawn de la statue à cet emplacement. Essaie dans une zone dégagée."));
            return StatueAdminService.Result.INVALID;
        }

        try {
            StatueDataService.get().save();
            return StatueAdminService.Result.SUCCESS;
        } catch (Exception exception) {
            entity.discard();
            StatueDataService.get().registry().remove(profile.id());
            throw exception;
        }
    }

    private static void sendSnapshot(ServerPlayer player) {
        var registry = StatueDataService.get().registry();
        var lines = new java.util.ArrayList<String>();
        lines.add("STATUES — " + registry.size() + " enregistrée(s)");
        for (var profile : registry.all()) {
            lines.add((profile.enabled() ? "● " : "○ ") + profile.name() + " [" + profile.id() + "]"
                    + " • " + profile.destinations().size() + " destination(s)");
            String assigned = profile.destinations().values().stream()
                    .map(destination -> destination.name().replace("|", "/").replace("\\n", " "))
                    .collect(java.util.stream.Collectors.joining(", "));
            lines.add("STATUEDETAIL|" + profile.id() + "|" + profile.specialization() + "|"
                    + profile.destinations().size() + "|" + assigned.substring(0, Math.min(180, assigned.length())));
        }
        lines.add("");
        lines.add("CATALOGUE PARTAGÉ — " + registry.catalog().size() + " entrée(s)");
        for (var destination : registry.catalog()) {
            lines.add((destination.enabled() ? "● " : "○ ") + destination.name() + " [" + destination.id() + "]");
            lines.add("DETAIL|" + destination.id() + "|" + destination.dimension() + "|"
                    + destination.x() + "|" + destination.y() + "|" + destination.z() + "|"
                    + destination.yaw() + "|" + destination.pitch() + "|"
                    + destination.description().replace("|", "/").replace("\\n", " ").substring(0,
                            Math.min(100, destination.description().length())));
        }
        PacketDistributor.sendToPlayer(player, new AdminSnapshotPayload(lines));
    }
}
