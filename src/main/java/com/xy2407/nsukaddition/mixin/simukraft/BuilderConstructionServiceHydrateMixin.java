package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 根治"已完工建筑任务重启后以 100% 进度复活、NPC 卡'收尾中'"。
 *
 * 根因：写队列按 key 合并、后提交者胜出。完工时 persistTask(completed@100) 先入队，随后
 * completeTask 用同一 key 提交 delete。若 completeTask 在某步抛异常（POI/place/register 等），
 * delete 不会入队，早期那条 completed@100 的 save 便残留在库；重启 hydrateTasks 又不过滤
 * completed 任务，把它当未完成任务恢复，导致 builder 已到 100% 无法再触发 completeTask 而永久
 * 卡在"收尾中"。
 *
 * 修法：恢复的唯一入口是 hydrateTasks 的异步装载（supplier lambda 内调用 loadBuildingTasks），
 * 在读取处把已完工（COMPLETED 或满进度）任务从结果中滤掉，同时用 level 真正删除其 DB 陈旧行，
 * 使其既不复活、也不反复产生垃圾行。半成品任务（currentBlockIndex < totalBlocks）不受影响。
 */
@Mixin(value = BuilderConstructionService.class, remap = false)
public abstract class BuilderConstructionServiceHydrateMixin {

    @Redirect(method = "lambda$hydrateTasks$0",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/storage/SimuSqliteStorage;loadBuildingTasks(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/List;"),
            require = 0, remap = false)
    private static List<BuildingTaskData> nsuk$cleanAndDeleteCompleted(ServerLevel level) {
        List<BuildingTaskData> loaded = SimuSqliteStorage.loadBuildingTasks(level);
        List<BuildingTaskData> active = new ArrayList<>(loaded.size());
        for (BuildingTaskData task : loaded) {
            if (task == null || task.citizenId() == null) {
                continue;
            }
            if (isCompleted(task)) {
                SimuSqliteStorage.deleteBuildingTask(level, task.citizenId());
            } else {
                active.add(task);
            }
        }
        return active;
    }

    /**
     * 安全网：hydrateTasks 方法体内唯一的 join() 处再滤一遍，杜绝任何路径把完工任务恢复。
     * join() 回调拿不到 level，故只做内存过滤；SL 上方的 lambda 拦截已负责删库。
     */
    @Redirect(method = "hydrateTasks",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1, remap = false)
    private static Object nsuk$filterResurrected(CompletableFuture<?> future) {
        Object joined = future.join();
        if (!(joined instanceof List<?> raw)) {
            return joined;
        }
        List<BuildingTaskData> active = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o instanceof BuildingTaskData task && task.citizenId() != null && !isCompleted(task)) {
                active.add(task);
            }
        }
        return active;
    }

    private static boolean isCompleted(BuildingTaskData task) {
        return BuildingTaskStatus.from(task.status()) == BuildingTaskStatus.COMPLETED
                || (task.totalBlocks() > 0 && task.currentBlockIndex() >= task.totalBlocks());
    }
}