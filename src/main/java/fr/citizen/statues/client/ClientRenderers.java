package fr.citizen.statues.client;
import fr.citizen.statues.CitizenStatues;
import fr.citizen.statues.entity.GuideStatueEntity;
import fr.citizen.statues.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
@EventBusSubscriber(modid=CitizenStatues.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientRenderers {
 private ClientRenderers(){}
 @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event){
  event.registerLayerDefinition(GuideStatueModel.LAYER,GuideStatueModel::createBodyLayer);
 }
 @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event){
  event.registerEntityRenderer(ModEntities.GUIDE_STATUE.get(),GuideStatueRenderer::new);
 }
 private static final class GuideStatueRenderer extends MobRenderer<GuideStatueEntity,GuideStatueModel>{
  private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("citizenstatues", "textures/entity/guide_statue.png");
  private GuideStatueRenderer(EntityRendererProvider.Context context){
   super(context,new GuideStatueModel(context.bakeLayer(GuideStatueModel.LAYER)),0.45F);shadowRadius=0.42F;
  }
  @Override public ResourceLocation getTextureLocation(GuideStatueEntity entity){return TEXTURE;}
 }
}
