package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.storage.BuildingTaskQueueStorage;
import com.xy2407.nsukaddition.server.building.BuildingTaskQueueService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * 任务结束(完成/取消/中断)时只删除"当前活跃任务"，保留该建筑师在 building_tasks 中的排队任务；
 * 同时触发追踪续接：若被结束的是城市追踪任务，清除追踪并自动追踪队列下一个未完成任务。
 * 原版 SimuSqliteStorage.deleteBuildingTask(level, citizenId) 按市民删除全部行，
 * 多任务存储下会把排队任务一并清掉；改为按活跃任务 taskId 精确删除。
 */
@Mixin(value = BuilderConstructionService.class, remap = false)
public abstract class BuilderConstructionServiceMultiTaskMixin {

    @Redirect(
            method = "cancelTask",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/storage/SimuSqliteStorage;deleteBuildingTask(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V"))
    private static void nsuk$cancelTaskDeleteActive(ServerLevel level, UUID citizenId) {
        BuildingTaskQueueStorage.deleteActiveTask(level, citizenId);
        BuildingTaskQueueService.onCitizenTaskEnded(level, citizenId);
    }

    @Redirect(
            method = "interruptTask",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/storage/SimuSqliteStorage;deleteBuildingTask(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V"))
    private static void nsuk$interruptTaskDeleteActive(ServerLevel level, UUID citizenId) {
        BuildingTaskQueueStorage.deleteActiveTask(level, citizenId);
        BuildingTaskQueueService.onCitizenTaskEnded(level, citizenId);
    }

    @Redirect(
            method = "completeTask",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/storage/SimuSqliteStorage;deleteBuildingTask(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V"))
    private static void nsuk$completeTaskDeleteActive(ServerLevel level, UUID citizenId) {
        BuildingTaskData active = common.cn.kafei.simukraft.storage.SimuSqliteStorage.loadBuildingTask(level, citizenId);
        if (active != null) {
            BuildingTaskQueueStorage.deleteByTaskIdSync(level, active.taskId());
        }
        BuildingTaskQueueService.onCitizenTaskEnded(level, citizenId);
    }
}