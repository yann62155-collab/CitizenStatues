package fr.citizen.statues.client;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.registry.ModEntities;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Temporary visible renderer registration. The bespoke CITIZEN character
 * model/texture replaces this placeholder in the dedicated art pass.
 */
@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {
    private ClientRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUIDE_STATUE.get(), ArmorStandRenderer::new);
    }
}
