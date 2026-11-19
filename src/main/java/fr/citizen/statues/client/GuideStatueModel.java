package fr.citizen.statues.client;
import fr.citizen.statues.entity.GuideStatueEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Custom block-built stone guardian, not a reskinned player model. */
public final class GuideStatueModel extends EntityModel<GuideStatueEntity> {
 public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("citizenstatues","guide_statue"),"main");
 private final ModelPart root,head,leftArm,rightArm;
 public GuideStatueModel(ModelPart root){this.root=root;head=root.getChild("head");leftArm=root.getChild("left_arm");rightArm=root.getChild("right_arm");}
 public static LayerDefinition createBodyLayer(){
  MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
  root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8),PartPose.offset(0,0,0));
  root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-5,0,-3,10,12,6),PartPose.offset(0,0,0));
  root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(0,0).addBox(-2,-2,-2,4,12,4),PartPose.offset(7,2,0));
  root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(0,0).addBox(-2,-2,-2,4,12,4),PartPose.offset(-7,2,0));
  root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,0).addBox(-2,0,-2,4,12,4),PartPose.offset(2.5F,12,0));
  root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,0).addBox(-2,0,-2,4,12,4),PartPose.offset(-2.5F,12,0));
  root.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(0,0).addBox(-5,-9,-5,10,2,10)
    .addBox(-2,-11,-2,4,2,4),PartPose.offset(0,0,0));
  root.addOrReplaceChild("left_shoulder",CubeListBuilder.create().texOffs(0,0)
    .addBox(-3,-3,-3,6,4,6),PartPose.offset(7,2,0));
  root.addOrReplaceChild("right_shoulder",CubeListBuilder.create().texOffs(0,0)
    .addBox(-3,-3,-3,6,4,6),PartPose.offset(-7,2,0));
  root.addOrReplaceChild("chest_rune",CubeListBuilder.create().texOffs(0,0)
    .addBox(-2,-3,-3.25F,4,5,1),PartPose.offset(0,4,0));
  root.addOrReplaceChild("pedestal",CubeListBuilder.create().texOffs(0,0)
    .addBox(-7,0,-6,14,2,12),PartPose.offset(0,24,0));
  return LayerDefinition.create(mesh,16,16);
 }
 @Override public void setupAnim(GuideStatueEntity entity,float limbSwing,float limbSwingAmount,float ageInTicks,float netHeadYaw,float headPitch){
  head.yRot=netHeadYaw*((float)Math.PI/180F);head.xRot=headPitch*((float)Math.PI/180F);
  leftArm.xRot=-0.72F;rightArm.xRot=-0.72F;leftArm.zRot=-0.18F;rightArm.zRot=0.18F;
 }
 @Override public void renderToBuffer(PoseStack poseStack,VertexConsumer consumer,int packedLight,int packedOverlay,int color){
  root.render(poseStack,consumer,packedLight,packedOverlay,color);
 }
}
