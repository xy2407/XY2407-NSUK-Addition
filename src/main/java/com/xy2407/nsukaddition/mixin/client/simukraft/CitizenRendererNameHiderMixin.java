package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.client.network.DiningOrderClientHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 就餐时隐藏 NPC 头顶的名称和状态标签，避免与气泡重叠。 */
@Mixin(CitizenRenderer.class)
public class CitizenRendererNameHiderMixin {

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true, remap = true)
    private void nsuk$hideNameTagWhenDining(CitizenEntity entity, Component component,
                                             PoseStack poseStack, MultiBufferSource bufferSource,
                                             int packedLight, float partialTick, CallbackInfo ci) {
        if (entity != null && DiningOrderClientHandler.isDining(entity.getUUID())) {
            ci.cancel();
        }
    }
}
