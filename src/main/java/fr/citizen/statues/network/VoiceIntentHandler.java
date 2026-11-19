package fr.citizen.statues.network;

import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.security.TeleportRequestService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VoiceIntentHandler {
    private VoiceIntentHandler() {}

    private static void reply(ServerPlayer player, boolean success, String message) {
        PacketDistributor.sendToPlayer(player, new VoiceIntentResultPayload(success, message));
    }

    public static void handle(VoiceIntentPayload payload, ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        var profile = StatueDataService.get().registry().get(payload.statueId()).orElse(null);
        if (profile == null || !profile.enabled()) {
            reply(player, false, "Ce guide n'est plus disponible.");
            return;
        }
        boolean close = !player.serverLevel().getEntitiesOfClass(
                fr.citizen.statues.entity.GuideStatueEntity.class,
                player.getBoundingBox().inflate(6.0D),
                statue -> payload.statueId().equals(statue.getProfileId())).isEmpty();
        if (!close) {
            reply(player, false, "Approche-toi du guide.");
            return;
        }
        var destination = fr.citizen.statues.voice.IntentMatcher.match(payload.utterance(), profile).orElse(null);
        if (destination == null) {
            reply(player, false, "Destination non reconnue. Réessaie.");
            return;
        }
        var result = TeleportRequestService.request(player, player.getServer(), profile.id(), destination.id(),
                System.currentTimeMillis());
        if (result == TeleportRequestService.Result.TELEPORTED) {
            reply(player, true, "Destination reconnue : " + destination.name() + ". Bon voyage !");
        } else {
            reply(player, false, "Téléportation refusée. Réessaie ou utilise le menu.");
        }
    }
}
