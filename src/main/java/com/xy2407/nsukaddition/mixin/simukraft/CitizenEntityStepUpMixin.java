package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 提升市民步高（maxUpStep 0.6 → 1.1）：让原版自动踏步能直接迈上 1 格台阶，
 * 这样 NPC 可平稳登上 Sable 物理结构地板或平地额外 1 格高的位置。
 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityStepUpMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nsukaddition$setFullStepUp(CallbackInfo ci) {
        var entity = (net.minecraft.world.entity.LivingEntity) (Object) this;
        var attr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        if (attr != null) {
            attr.setBaseValue(1.1D);
        }
    }
}