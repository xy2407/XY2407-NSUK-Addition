package com.xy2407.nsukaddition.server.building;

import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.util.NpcWorkChunkLoadService;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/** 建造任务运行时控制器，通过反射操作内部状态以停止和恢复任务。 */
public final class BuilderTaskControl {

    private static final Field LEVEL_RUNTIMES_FIELD;
    private static final Field TASKS_BY_CITIZEN_FIELD;
    private static final Class<?> LEVEL_RUNTIME_CLASS;
    private static final Class<?> TASK_RUNTIME_CLASS;
    /** NpcWorkChunkLoadService.release 的真实签名引用于运行时探测(新版 UUID / 旧版 BlockPos)。 */
    private static final Method RELEASE_METHOD;

    static {
        try {
            LEVEL_RUNTIME_CLASS = Class.forName("common.cn.kafei.simukraft.building.BuilderConstructionService$LevelRuntime");
            TASK_RUNTIME_CLASS = Class.forName("common.cn.kafei.simukraft.building.BuilderConstructionService$TaskRuntime");
            LEVEL_RUNTIMES_FIELD = BuilderConstructionService.class.getDeclaredField("LEVEL_RUNTIMES");
            LEVEL_RUNTIMES_FIELD.setAccessible(true);
            TASKS_BY_CITIZEN_FIELD = LEVEL_RUNTIME_CLASS.getDeclaredField("tasksByCitizen");
            TASKS_BY_CITIZEN_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BuilderConstructionService reflection", e);
        }
    }

    static {
        Method release = null;
        try {
            release = NpcWorkChunkLoadService.class.getMethod("release", ServerLevel.class, UUID.class);
        } catch (ReflectiveOperationException ignored) {
            try {
                release = NpcWorkChunkLoadService.class.getMethod("release", ServerLevel.class, BlockPos.class);
            } catch (ReflectiveOperationException ignored2) {
                release = null;
            }
        }
        RELEASE_METHOD = release;
    }

    private BuilderTaskControl() {
    }

    public static boolean stopRuntime(ServerLevel level, UUID citizenId) {
        return removeTaskRuntime(level, citizenId) != null;
    }

    public static boolean stopRuntimeIfTask(ServerLevel level, UUID citizenId, UUID taskId) {
        if (taskId == null) {
            return false;
        }
        return removeTaskRuntimeIfTask(level, citizenId, taskId) != null;
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
                releaseWorkChunk(level, task);
            }
            return removed;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object removeTaskRuntimeIfTask(ServerLevel level, UUID citizenId, UUID taskId) {
        try {
            String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
            ConcurrentMap<String, Object> runtimes = (ConcurrentMap<String, Object>) LEVEL_RUNTIMES_FIELD.get(null);
            Object levelRuntime = runtimes.get(key);
            if (levelRuntime == null) {
                return null;
            }
            ConcurrentMap<UUID, Object> tasks = (ConcurrentMap<UUID, Object>) TASKS_BY_CITIZEN_FIELD.get(levelRuntime);
            Object taskRuntime = tasks.get(citizenId);
            if (taskRuntime == null) {
                return null;
            }
            BuildingTaskData running = taskFromRuntime(taskRuntime);
            if (running == null || !taskId.equals(running.taskId())) {
                return null;
            }
            Object removed = tasks.remove(citizenId);
            if (removed != null) {
                releaseWorkChunk(level, running);
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

    /** releaseWorkChunk：按运行时真实签名释放 NPC 工作区块租约(新版 UUID / 旧版 BlockPos 均可)。 */
    private static void releaseWorkChunk(ServerLevel level, BuildingTaskData task) {
        if (RELEASE_METHOD == null || task == null) {
            return;
        }
        try {
            if (RELEASE_METHOD.getParameterTypes()[1] == UUID.class) {
                RELEASE_METHOD.invoke(null, level, task.taskId());
            } else {
                RELEASE_METHOD.invoke(null, level, task.buildBoxPos());
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
