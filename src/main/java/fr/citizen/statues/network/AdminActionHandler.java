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
                case "catalog-create-here" -> StatueAdminService.createCatalogDestinationHere(actor,
                        payload.destinationId(), payload.statueId());
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

    private static void sendSnapshot(ServerPlayer player) {
        var registry = StatueDataService.get().registry();
        var lines = new java.util.ArrayList<String>();
        lines.add("STATUES — " + registry.size() + " enregistrée(s)");
        for (var profile : registry.all()) {
            lines.add((profile.enabled() ? "● " : "○ ") + profile.name() + " [" + profile.id() + "]"
                    + " • " + profile.destinations().size() + " destination(s)");
        }
        lines.add("");
        lines.add("CATALOGUE PARTAGÉ — " + registry.catalog().size() + " entrée(s)");
        for (var destination : registry.catalog()) {
            lines.add((destination.enabled() ? "● " : "○ ") + destination.name() + " [" + destination.id() + "]");
        }
        PacketDistributor.sendToPlayer(player, new AdminSnapshotPayload(lines));
    }
}
