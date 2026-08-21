package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** 建筑任务删除（终止/取消按钮）立即落库：标记立即模式让 F 兜底对该次删除走 submitPriority，避免 20 tick 延迟导致重启后任务复活。 */
@Mixin(value = SimuSqliteStorage.class, remap = false)
public abstract class SimuSqliteStorageDeleteBuildingTaskMixin {

    @Inject(method = "deleteBuildingTask(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V",
            at = @At("HEAD"), remap = false)
    private static void nsuk$markImmediate(ServerLevel level, UUID citizenId, CallbackInfo ci) {
        if (WriteBatchBuffer.isWriterThread()) {
            return;
        }
        WriteBatchBuffer.setImmediate(true);
    }

    @Inject(method = "deleteBuildingTask(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V",
            at = @At("RETURN"), remap = false)
    private static void nsuk$clearImmediate(ServerLevel level, UUID citizenId, CallbackInfo ci) {
        WriteBatchBuffer.setImmediate(false);
    }
}
