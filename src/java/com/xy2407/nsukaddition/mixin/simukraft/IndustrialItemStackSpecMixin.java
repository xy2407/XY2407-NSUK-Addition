package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** 修改 IndustrialItemStackSpec，重写判空逻辑以检查所有字段是否为空。 */
@Mixin(IndustrialItemStackSpec.class)
public class IndustrialItemStackSpecMixin {

    @Overwrite(remap = false)
    public boolean isEmpty() {
        IndustrialItemStackSpec self = (IndustrialItemStackSpec) (Object) this;
        return self.itemId().isBlank()
                && self.itemTag().isBlank()
                && self.itemStackText().isBlank()
                && self.customDataText().isBlank();
    }
}
