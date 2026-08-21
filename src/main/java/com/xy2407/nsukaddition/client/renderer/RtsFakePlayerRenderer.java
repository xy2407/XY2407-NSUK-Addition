package com.xy2407.nsukaddition.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** RTS 假人渲染器：使用玩家模型和本地玩家皮肤渲染假人实体。 */
@OnlyIn(Dist.CLIENT)
public class RtsFakePlayerRenderer extends LivingEntityRenderer<RtsFakePlayerEntity, PlayerModel<RtsFakePlayerEntity>> {

    public RtsFakePlayerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(RtsFakePlayerEntity entity) {
        AbstractClientPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerSkin skin = localPlayer.getSkin();
            return skin.texture();
        }
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }

    @Override
    public void render(RtsFakePlayerEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerModel<RtsFakePlayerEntity> model = getModel();
        model.rightArm.xRot = 0.0F;
        model.leftArm.xRot = 0.0F;
        model.rightArm.zRot = 0.0F;
        model.leftArm.zRot = 0.0F;
        model.rightLeg.xRot = 0.0F;
        model.leftLeg.xRot = 0.0F;

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
