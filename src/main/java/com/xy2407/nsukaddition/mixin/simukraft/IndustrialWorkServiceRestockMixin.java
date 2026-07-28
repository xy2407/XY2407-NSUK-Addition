package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockService;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialWorkService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修改 IndustrialWorkService，在配方步骤开始前（step 0）从仓库自动补货缺少的材料。 */
@Mixin(IndustrialWorkService.class)
public abstract class IndustrialWorkServiceRestockMixin {

    @Inject(method = "executeStep", at = @At("HEAD"), remap = false)
    private static void nsuk$preRestockOnStep0(ServerLevel level,
                                               IndustrialBoxManager manager,
                                               IndustrialBoxData data,
                                               @Coerce Object boxRuntime,
                                               @Coerce Object building,
                                               IndustrialDefinition definition,
                                               IndustrialDefinition.RecipeDefinition recipe,
                                               @Coerce Object worker,
                                               @Coerce Object entity,
                                               IndustrialDefinition.StepDefinition step,
                                               long gameTime,
                                               CallbackInfoReturnable<Object> cir) {
        if (data.currentStep() != 0) return;
        if (!AutoRestockConfig.isEnabled(data.boxPos())) return;

        AutoRestockService.restockIndustrialInputs(level, data.boxPos());
    }
}
