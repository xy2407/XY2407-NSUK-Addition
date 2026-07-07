package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialWorkService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修改 IndustrialWorkService，在 requireInputs 之前从仓库自动补货缺少的材料。 */
@Mixin(IndustrialWorkService.class)
public abstract class IndustrialWorkServiceRestockMixin {

    @Inject(method = "requireInputs", at = @At("HEAD"), remap = false)
    private static void nsuk$preRestockInputs(ServerLevel level,
                                              IndustrialBoxManager manager,
                                              IndustrialBoxData data,
                                              PlacedBuildingRecord building,
                                              IndustrialDefinition definition,
                                              IndustrialDefinition.RecipeDefinition recipe,
                                              IndustrialDefinition.StepDefinition step,
                                              CallbackInfoReturnable<Object> cir) {

        if (!AutoRestockConfig.isEnabled(data.boxPos())) {
            return;
        }

        NsukAddition.LOGGER.info("[RestockMixin] requireInputs HEAD for box at {}", data.boxPos());
        AutoRestockService.restockIndustrialInputs(level, data.boxPos());
    }
}
