package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialBlockClusterHarvestService;
import common.cn.kafei.simukraft.industrial.IndustrialBlockClusterHarvestService.ActionResult;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialWorkService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/** 伐木缺树苗时跳过补种并继续后续任务，避免待补种位置残留导致机器永久等待树苗。 */
@Mixin(IndustrialWorkService.class)
public abstract class IndustrialWorkServiceSaplingMixin {

    @Redirect(
            method = "harvestBlockClusters",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/industrial/IndustrialBlockClusterHarvestService;execute(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/industrial/IndustrialBoxManager;Lcommon/cn/kafei/simukraft/industrial/IndustrialBoxData;Lcommon/cn/kafei/simukraft/building/PlacedBuildingRecord;Lcommon/cn/kafei/simukraft/industrial/IndustrialDefinition;Lcommon/cn/kafei/simukraft/industrial/IndustrialDefinition$StepDefinition;Lcommon/cn/kafei/simukraft/entity/CitizenEntity;Ljava/util/UUID;)Lcommon/cn/kafei/simukraft/industrial/IndustrialBlockClusterHarvestService$ActionResult;"),
            remap = false
    )
    private static ActionResult nsukaddition$resumeWhenNoSapling(ServerLevel level, IndustrialBoxManager manager,
                                                                  IndustrialBoxData data, PlacedBuildingRecord building,
                                                                  IndustrialDefinition definition, IndustrialDefinition.StepDefinition step,
                                                                  CitizenEntity entity, UUID workerId) {
        ActionResult result = IndustrialBlockClusterHarvestService.execute(
                level, manager, data, building, definition, step, entity, workerId);
        if (result == ActionResult.MISSING_INPUTS) {
            return ActionResult.HARVESTED;
        }
        return result;
    }
}