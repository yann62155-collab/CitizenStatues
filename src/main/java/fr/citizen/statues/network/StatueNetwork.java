package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.client.StatueAdminScreen;
import fr.citizen.statues.client.PlayerDestinationsScreen;\nimport fr.citizen.statues.client.VoiceRecognitionClient;\nimport fr.citizen.statues.network.VoiceIntentPayload;\nimport fr.citizen.statues.network.VoiceIntentHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-side receiver for the server-authoritative admin snapshot. */
@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StatueNetwork {
    private StatueNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(AdminSnapshotPayload.TYPE, AdminSnapshotPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() ->
                                Minecraft.getInstance().setScreen(new StatueAdminScreen(payload.lines()))))
                .playToClient(PlayerDestinationsPayload.TYPE, PlayerDestinationsPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                                new PlayerDestinationsScreen(payload.statueId(), payload.title(), payload.destinations()))))
                .playToServer(VoiceIntentPayload.TYPE, VoiceIntentPayload.STREAM_CODEC,\n                        (payload, context) -> context.enqueueWork(() -> VoiceIntentHandler.handle(payload, context.player())))\n                .playToServer(PlayerTeleportPayload.TYPE, PlayerTeleportPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> PlayerTeleportHandler.handle(payload, context.player())))
                .playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() ->
                                AdminActionHandler.handle(payload, context.player())));
    }
}
