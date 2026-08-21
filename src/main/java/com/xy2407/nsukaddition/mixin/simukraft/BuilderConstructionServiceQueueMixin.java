package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.building.BuildingTaskQueueService;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 建筑任务队列驱动：tick 收尾检查队列开工、启动恢复排队任务、拆盒清队列。 */
@Mixin(BuilderConstructionService.class)
public abstract class BuilderConstructionServiceQueueMixin {

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private static void nsuk$tickQueues(ServerLevel level, CallbackInfo ci) {
        BuildingTaskQueueService.maybeRecoverQueued(level);
        BuildingTaskQueueService.tickQueues(level);
    }

    @Inject(method = "interruptTasksByBuildBox", at = @At("HEAD"), remap = false)
    private static void nsuk$clearBoxQueue(ServerLevel level, BlockPos buildBoxPos, String reason, CallbackInfo ci) {
        BuildingTaskQueueService.clearBoxQueue(level, buildBoxPos);
    }
}