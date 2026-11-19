package fr.citizen.statues.client;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderer registration; the dedicated character model is a later art task. */
@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {
    private ClientRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUIDE_STATUE.get(), GuideStatueRenderer::new);
    }

    private static final class GuideStatueRenderer extends EntityRenderer<GuideStatueEntity> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.fromNamespaceAndPath(CitizenStatues.MOD_ID, "textures/entity/guide_statue.png");

        private GuideStatueRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.45F;
        }

        @Override
        public ResourceLocation getTextureLocation(GuideStatueEntity entity) {
            return TEXTURE;
        }
    }
}
