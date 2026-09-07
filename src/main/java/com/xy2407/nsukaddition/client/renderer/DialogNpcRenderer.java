package com.xy2407.nsukaddition.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 城市对话NPC渲染器：使用玩家模型并固定使用模组皮肤贴图。 */
@OnlyIn(Dist.CLIENT)
public class DialogNpcRenderer extends LivingEntityRenderer<DialogNpcEntity, PlayerModel<DialogNpcEntity>> {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "textures/skin/skin.png");

    public DialogNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(DialogNpcEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(DialogNpcEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        PlayerModel<DialogNpcEntity> model = getModel();
        model.rightArm.xRot = 0.0F;
        model.leftArm.xRot = 0.0F;
        model.rightArm.zRot = 0.0F;
        model.leftArm.zRot = 0.0F;
        model.rightLeg.xRot = 0.0F;
        model.leftLeg.xRot = 0.0F;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}