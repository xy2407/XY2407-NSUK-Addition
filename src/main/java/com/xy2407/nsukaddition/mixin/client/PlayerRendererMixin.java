package com.xy2407.nsukaddition.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 模式下隐藏本地玩家实体渲染（由假人替身代替显示）。 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$hideLocalPlayerInRts(
            AbstractClientPlayer entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (RtsModeManager.isActive() && entity == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }
}
