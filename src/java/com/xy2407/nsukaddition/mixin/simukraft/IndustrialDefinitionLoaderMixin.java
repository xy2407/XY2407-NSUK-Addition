package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 提高工业定义加载器的步骤数上限，支持大型配方。 */
@Mixin(IndustrialDefinitionLoader.class)
public class IndustrialDefinitionLoaderMixin {

    @ModifyConstant(method = "parseSteps", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 256), remap = false)
    private static int xy2407$modifyMaxSteps(int original) {
        return 1024;
    }

    @ModifyConstant(method = "appendRepeatedSteps", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 256), remap = false)
    private static int xy2407$modifyMaxStepsRepeat(int original) {
        return 1024;
    }
}
