package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 视角下在 Camera.setup 末尾覆盖相机位置和旋转，使用 partialTicks 插值实现平滑渲染。 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("RETURN"))
    private void nsukaddition$overrideCameraPosition(
            BlockGetter level, Entity focusedEntity,
            boolean detached, boolean mirrored,
            float partialTicks, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            Vec3 pos = RtsModeManager.getRenderCameraPos(partialTicks);
            if (pos != null) {
                setPosition(pos);
                setRotation(
                        RtsModeManager.getRenderCameraYaw(partialTicks),
                        RtsModeManager.getRenderCameraPitch(partialTicks)
                );
            }
        }
    }
}
