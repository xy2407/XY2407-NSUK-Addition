package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 保留仅以 customData 限定的工业原料规格，使其参与输入校验与扣料。
 * 业务需求：工业建筑（冶炼厂等）用标签匹配"同标签任意物品"的消耗，
 * 纯 customData 规格用于限定物品的附加数据（NBT），不能因 isEmpty() 被过滤。
 */
@Mixin(IndustrialItemStackSpec.class)
public abstract class IndustrialItemStackSpecMixin {

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukaddition$keepCustomDataOnlySpec(CallbackInfoReturnable<Boolean> cir) {
        IndustrialItemStackSpec self = (IndustrialItemStackSpec) (Object) this;
        if (!self.customDataText().isBlank()) {
            cir.setReturnValue(false);
        }
    }
}
