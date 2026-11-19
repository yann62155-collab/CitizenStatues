package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
public final class StatueNetworkServer {
    private StatueNetworkServer() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(AdminSnapshotPayload.TYPE, AdminSnapshotPayload.STREAM_CODEC, (payload, context) -> {})
                .playToClient(PlayerDestinationsPayload.TYPE, PlayerDestinationsPayload.STREAM_CODEC, (payload, context) -> {})
                .playToClient(VoiceIntentResultPayload.TYPE, VoiceIntentResultPayload.STREAM_CODEC, (payload, context) -> {})
                .playToServer(VoiceIntentPayload.TYPE, VoiceIntentPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> VoiceIntentHandler.handle(payload, context.player())))
                .playToServer(PlayerTeleportPayload.TYPE, PlayerTeleportPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> PlayerTeleportHandler.handle(payload, context.player())))
                .playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> AdminActionHandler.handle(payload, context.player())));
    }
}
