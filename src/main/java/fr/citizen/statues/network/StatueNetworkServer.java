package fr.citizen.statues.network;

import fr.citizen.statues.CitizenStatues;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Dedicated-server registration; the payload is outbound only on this physical side. */
@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
public final class StatueNetworkServer {
    private StatueNetworkServer() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(AdminSnapshotPayload.TYPE, AdminSnapshotPayload.STREAM_CODEC, (payload, context) -> { })
                .playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC, (payload, context) -> { });
    }
}
