package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.client.StatueAdminScreen;
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
        event.registrar("1").playToClient(
                AdminSnapshotPayload.TYPE,
                AdminSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new StatueAdminScreen(payload.lines()))));
    }
}
