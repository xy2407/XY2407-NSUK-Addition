package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 模式下冻结选中市民的 SimuKraft 工作逻辑，但保留原版 LivingEntity.tick() 的 AI 和导航移动。 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityRtsMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/entity/CitizenEntity;syncClientWorkSwingPulse()V", remap = false), cancellable = true)
    private void nsukaddition$freezeWhenSelected(CallbackInfo ci) {
        CitizenEntity self = (CitizenEntity) (Object) this;
        if (RtsCitizenTaskManager.isFrozen(self.getUUID())) {
            ci.cancel();
        }
    }
}