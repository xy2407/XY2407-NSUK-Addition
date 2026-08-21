package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 提升市民步高（maxUpStep 0.6 → 1.1）：
 * Sable 物理结构碰撞的自动上台阶（SubLevelEntityCollision.tryStepUp）以实体 maxUpStep
 * 为抬升上限。原版默认 0.6 上不了 1 格高的台阶，导致 NPC 在"普通方块 ↔ 物理化结构"
 * 的 1 格高差边界卡住（快照合并后 A* 认为可走，实际迈不上去）。
 * 步高 1.1 后 NPC 能正常迈上 1 格台阶（普通世界与结构边界一致），副作用极小。
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