package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.storage.BuildingTaskQueueStorage;
import com.xy2407.nsukaddition.server.building.BuildingTaskQueueService;
import com.xy2407.nsukaddition.server.building.BuildTaskTrackedState;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.network.building.BuildBoxStartConstructionPacket;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/** 建筑任务入队入口：新任务不再顶替旧任务，盒忙时转为排队。 */
@Mixin(BuildBoxStartConstructionPacket.class)
public abstract class BuildBoxStartConstructionQueueMixin {

    @Redirect(method = "handle", remap = false, at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/building/BuilderConstructionService;cancelTask(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V"))
    private static void nsuk$skipCancelTask(ServerLevel level, UUID citizenId) {
    }

    @Redirect(method = "handle", remap = false, at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/building/BuilderConstructionService;startTask(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/building/BuildingTaskData;)V"))
    private static void nsuk$startOrQueue(ServerLevel level, BuildingTaskData task) {
        if (BuildingTaskQueueService.hasRunningTask(level, task.buildBoxPos())) {
            BuildingTaskQueueService.enqueue(level, task);
        } else {
            if (task.cityId() != null) {
                BuildTaskTrackedState.setTrackedTask(level, task.cityId(), task.taskId());
            }
            BuilderConstructionService.startTask(level, task.withStatus(BuildingTaskStatus.BUILDING));
            BuildingTaskQueueStorage.flushRunning(level, task.citizenId());
        }
    }
}
