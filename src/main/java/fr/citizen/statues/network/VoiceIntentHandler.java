package fr.citizen.statues.network;

import fr.citizen.statues.data.Destination;
import fr.citizen.statues.data.StatueDataService;
import fr.citizen.statues.network.VoiceIntentResultPayload.Action;
import fr.citizen.statues.security.TeleportRequestService;
import fr.citizen.statues.voice.ConversationPrompts;
import fr.citizen.statues.voice.IntentMatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;

public final class VoiceIntentHandler {
    private VoiceIntentHandler() {}

    private static void reply(ServerPlayer player, Action action, String message) {
        PacketDistributor.sendToPlayer(player, new VoiceIntentResultPayload(action, message));
    }

    public static void handle(VoiceIntentPayload payload, Player sender) {
        if (!(sender instanceof ServerPlayer player) || player.getServer() == null) return;
        var profile = StatueDataService.get().registry().get(payload.statueId()).orElse(null);
        if (profile == null || !profile.enabled()) {
            reply(player, Action.UNAVAILABLE, "Ce guide n'est plus disponible.");
            return;
        }
        boolean close = !player.serverLevel().getEntitiesOfClass(
                fr.citizen.statues.entity.GuideStatueEntity.class,
                player.getBoundingBox().inflate(6.0D),
                statue -> payload.statueId().equals(statue.getProfileId())).isEmpty();
        if (!close) {
            reply(player, Action.UNAVAILABLE, "Approche-toi du guide.");
            return;
        }

        IntentMatcher.Result result = IntentMatcher.analyze(payload.hypotheses(), profile);
        switch (result.command()) {
            case CANCEL -> {
                reply(player, Action.CANCELLED, "D'accord. Bonne route !");
                return;
            }
            case MENU -> {
                reply(player, Action.OPEN_MENU, "Voici la liste des lieux disponibles.");
                return;
            }
            default -> { }
        }

        Destination destination = result.destination();
        if (destination == null) {
            var available = profile.destinations().values().stream().filter(Destination::enabled)
                    .sorted(Comparator.comparing(Destination::name, String.CASE_INSENSITIVE_ORDER)).toList();
            reply(player, Action.RETRY, ConversationPrompts.forMisunderstanding(result, available));
            return;
        }
        var outcome = TeleportRequestService.request(player, player.getServer(), profile.id(), destination.id(),
                System.currentTimeMillis());
        if (outcome == TeleportRequestService.Result.COOLDOWN) {
            reply(player, Action.RETRY, "J'ai compris « " + destination.name()
                    + " ». Attends quelques secondes avant de repartir, puis redis-le.");
        } else if (outcome == TeleportRequestService.Result.TELEPORTED) {
            reply(player, Action.TELEPORTED, "Destination reconnue : " + destination.name() + ". Bon voyage !");
        } else {
            reply(player, Action.RETRY, "J'ai compris « " + destination.name()
                    + " », mais la téléportation est refusée pour l'instant. Réessaie ou utilise le menu.");
        }
    }
}
