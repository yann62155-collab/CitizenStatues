package fr.citizen.statues.network;

import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.security.TeleportRequestService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class VoiceIntentHandler {
    private VoiceIntentHandler() {}

    public static void handle(VoiceIntentPayload payload, ServerPlayer player) {
        if (player == null || player.server == null) return;
        var profile = StatueDataService.get().registry().get(payload.statueId()).orElse(null);
        if (profile == null || !profile.enabled()) {
            player.sendSystemMessage(Component.literal("Ce guide n'est plus disponible."));
            return;
        }
        boolean close = player.serverLevel().getEntitiesOfClass(
                fr.citizen.statues.entity.GuideStatueEntity.class,
                player.getBoundingBox().inflate(6.0D),
                statue -> payload.statueId().equals(statue.getProfileId())).size() > 0;
        if (!close) {
            player.sendSystemMessage(Component.literal("Approche-toi du guide pour utiliser la commande vocale."));
            return;
        }
        var destination = fr.citizen.statues.voice.IntentMatcher.match(payload.utterance(), profile).orElse(null);
        if (destination == null) {
            player.sendSystemMessage(Component.literal("Je n'ai pas reconnu cette destination. Réessaie en prononçant son nom."));
            return;
        }
        var result = TeleportRequestService.request(player, player.server, profile.id(), destination.id(),
                System.currentTimeMillis());
        if (result == TeleportRequestService.Result.TELEPORTED) {
            player.sendSystemMessage(Component.literal("Destination reconnue : " + destination.name() + ". Bon voyage !"));
        } else {
            player.sendSystemMessage(Component.literal("Impossible d'effectuer ce déplacement. Utilise le menu du guide."));
        }
    }
}
