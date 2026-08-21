package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RTS 正交投影注入：RTS 视角开启正交模式时，把 GameRenderer.getProjectionMatrix(double) 返回的
 * 透视矩阵替换为正交矩阵（渲染投影与 Frustum 剔除共用此方法，同步生效）。
 * 方法名采用 Mojang 映射（getProjectionMatrix），Yarn 映射下叫 getBasicProjectionMatrix。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererProjectionMixin {

    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void nsukaddition$replaceWithOrtho(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!RtsModeManager.isActive() || !RtsModeManager.isOrthoEnabled()) {
            return;
        }
        double halfWidth = RtsModeManager.getOrthoHalfWidth();
        if (halfWidth <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float aspect = mc != null && mc.getWindow() != null
                ? (float) mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight()
                : 1.0F;
        double halfHeight = halfWidth / aspect;
        cir.setReturnValue(new Matrix4f().ortho(
                (float) -halfWidth, (float) halfWidth,
                (float) -halfHeight, (float) halfHeight,
                0.05F, 2000.0F
        ));
    }
}
