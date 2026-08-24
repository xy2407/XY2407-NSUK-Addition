package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.RedPanda;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 优化红熊猫躲避评估频率：canUse 加 40tick 冷却，避免两个 AvoidGoal 每 tick 全量实体扫描。 */
@Mixin(targets = "com.mysticsbiomes.common.entity.RedPanda$RedPandaAvoidGoal")
public abstract class RedPandaAvoidGoalMixin {

    @Shadow
    @Final
    private RedPanda redPanda;

    @Unique
    private int xy2407$lastEvaluateTick = -1;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void xy2407$cooldownCanUse(CallbackInfoReturnable<Boolean> cir) {
        int tick = this.redPanda.tickCount;
        if (tick - this.xy2407$lastEvaluateTick < 80) {
            cir.setReturnValue(false);
            return;
        }
        this.xy2407$lastEvaluateTick = tick;
    }
}