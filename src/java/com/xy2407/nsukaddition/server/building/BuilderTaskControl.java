package com.xy2407.nsukaddition.server.building;

import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.util.NpcWorkChunkLoadService;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/** 建造任务运行时控制器，通过反射操作内部状态以停止和恢复任务。 */
public final class BuilderTaskControl {

    private static final Field LEVEL_RUNTIMES_FIELD;
    private static final Field TASKS_BY_CITIZEN_FIELD;
    private static final Field SAVE_FUTURE_FIELD;
    private static final Class<?> LEVEL_RUNTIME_CLASS;
    private static final Class<?> TASK_RUNTIME_CLASS;

    static {
        try {
            LEVEL_RUNTIME_CLASS = Class.forName("common.cn.kafei.simukraft.building.BuilderConstructionService$LevelRuntime");
            TASK_RUNTIME_CLASS = Class.forName("common.cn.kafei.simukraft.building.BuilderConstructionService$TaskRuntime");
            LEVEL_RUNTIMES_FIELD = BuilderConstructionService.class.getDeclaredField("LEVEL_RUNTIMES");
            LEVEL_RUNTIMES_FIELD.setAccessible(true);
            TASKS_BY_CITIZEN_FIELD = LEVEL_RUNTIME_CLASS.getDeclaredField("tasksByCitizen");
            TASKS_BY_CITIZEN_FIELD.setAccessible(true);
            SAVE_FUTURE_FIELD = TASK_RUNTIME_CLASS.getDeclaredField("saveFuture");
            SAVE_FUTURE_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BuilderConstructionService reflection", e);
        }
    }

    private BuilderTaskControl() {
    }

    public static boolean stopRuntime(ServerLevel level, UUID citizenId) {
        Object removed = removeTaskRuntime(level, citizenId);
        if (removed == null) {
            return false;
        }
        waitForPendingSave(removed);
        return true;
    }

    public static void resumeTask(ServerLevel level, BuildingTaskData task) {
        BuilderConstructionService.startTask(level, task);
    }

    private static Object removeTaskRuntime(ServerLevel level, UUID citizenId) {
        try {
            String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
            ConcurrentMap<String, Object> runtimes = (ConcurrentMap<String, Object>) LEVEL_RUNTIMES_FIELD.get(null);
            Object levelRuntime = runtimes.get(key);
            if (levelRuntime == null) {
                return null;
            }
            ConcurrentMap<UUID, Object> tasks = (ConcurrentMap<UUID, Object>) TASKS_BY_CITIZEN_FIELD.get(levelRuntime);
            Object removed = tasks.remove(citizenId);
            if (removed == null) {
                return null;
            }
            BuildingTaskData task = taskFromRuntime(removed);
            if (task != null) {
                NpcWorkChunkLoadService.release(level, task.buildBoxPos());
            }
            return removed;
        } catch (Exception e) {
            return null;
        }
    }

    private static BuildingTaskData taskFromRuntime(Object taskRuntime) {
        try {
            Field taskField = TASK_RUNTIME_CLASS.getDeclaredField("task");
            taskField.setAccessible(true);
            return (BuildingTaskData) taskField.get(taskRuntime);
        } catch (Exception e) {
            return null;
        }
    }

    private static void waitForPendingSave(Object taskRuntime) {
        try {
            CompletableFuture<?> future = (CompletableFuture<?>) SAVE_FUTURE_FIELD.get(taskRuntime);
            if (future != null && !future.isDone()) {
                future.join();
            }
        } catch (Exception ignored) {
        }
    }
}
