package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.industrial.JumpRescueSkipHolder;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** jump 步骤期间跳过 rescueFromWall 的碰撞传送，防止 NPC 站在设备上方时被传送走。 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityRescueSkipMixin {

    @Inject(method = "rescueFromWall", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$skipRescueDuringJump(boolean immediate, CallbackInfo ci) {
        CitizenEntity self = (CitizenEntity) (Object) this;
        if (JumpRescueSkipHolder.shouldSkip(self.getUUID())) {
            ci.cancel();
        }
    }
}
