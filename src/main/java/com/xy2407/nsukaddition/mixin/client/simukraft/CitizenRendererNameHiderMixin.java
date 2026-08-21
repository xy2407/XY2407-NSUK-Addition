package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.client.network.DiningOrderClientHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 就餐或距离玩家超过12格时隐藏 NPC 头顶的名称和状态标签。 */
@Mixin(CitizenRenderer.class)
public class CitizenRendererNameHiderMixin {

    private static final double HIDE_DISTANCE_SQR = 12.0D * 12.0D;

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true, remap = true)
    private void nsuk$hideNameTagWhenDiningOrFar(CitizenEntity entity, Component component,
                                                  PoseStack poseStack, MultiBufferSource bufferSource,
                                                  int packedLight, float partialTick, CallbackInfo ci) {
        if (entity == null) return;
        if (DiningOrderClientHandler.isDining(entity.getUUID())) {
            ci.cancel();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity != null && mc.cameraEntity.distanceToSqr(entity) > HIDE_DISTANCE_SQR) {
            ci.cancel();
        }
    }
}
