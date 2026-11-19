package fr.citizen.statues.client;

import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Visible humanoid fallback renderer; replace its skin with the final custom statue texture later. */
@EventBusSubscriber(modid = CitizenStatues.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {
    private ClientRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUIDE_STATUE.get(), GuideStatueRenderer::new);
    }

    private static final class GuideStatueRenderer
            extends HumanoidMobRenderer<GuideStatueEntity, HumanoidModel<GuideStatueEntity>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

        private GuideStatueRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
            this.shadowRadius = 0.45F;
        }

        @Override
        public ResourceLocation getTextureLocation(GuideStatueEntity entity) {
            return TEXTURE;
        }
    }
}
