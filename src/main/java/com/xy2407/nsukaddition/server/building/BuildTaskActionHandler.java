package com.xy2407.nsukaddition.server.building;

import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.xy2407.nsukaddition.server.SidebarDataCache;
import com.xy2407.nsukaddition.common.storage.BuildingTaskQueueStorage;

import java.util.UUID;

/** 建造任务操作处理器，按任务 ID 区分处理暂停、恢复、追踪和终止，互不影响其它任务。 */
public final class BuildTaskActionHandler {

    private BuildTaskActionHandler() {
    }

    public static void handle(ServerLevel level, ServerPlayer player,
                              UUID citizenId, UUID taskId,
                              com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket.Action action) {
        var cityOpt = CityManager.get(level).getPlayerCity(player.getUUID());
        if (cityOpt.isEmpty()) return;
        UUID cityId = cityOpt.get().cityId();

        BuildingTaskData task = SimuSqliteStorage.loadBuildingTask(level, citizenId);

        if (action == com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket.Action.ABORT) {
            handleAbort(level, cityId, citizenId, taskId);
            SidebarDataCache.refreshAsync(level);
            return;
        }
        if (action == com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket.Action.TRACK) {
            BuildingTaskQueueService.switchTrack(level, cityId, taskId);
            SidebarDataCache.refreshAsync(level);
            return;
        }

        if (task == null || !cityId.equals(task.cityId())) return;
        switch (action) {
            case PAUSE -> {
                if (isRunningTask(level, citizenId, taskId)) {
                    handlePause(level, cityId, citizenId);
                }
            }
            case RESUME -> handleResume(level, cityId, citizenId, task);
            case ABORT -> {
            }
        }
        SidebarDataCache.refreshAsync(level);
    }

    private static boolean isRunningTask(ServerLevel level, UUID citizenId, UUID taskId) {
        if (taskId == null) {
            return false;
        }
        try {
            Class<?> service = common.cn.kafei.simukraft.building.BuilderConstructionService.class;
            java.lang.reflect.Field runtimesField = service.getDeclaredField("LEVEL_RUNTIMES");
            runtimesField.setAccessible(true);
            Object runtimes = runtimesField.get(null);
            String key = common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level)
                    .toLowerCase(java.util.Locale.ROOT);
            Object runtime = ((java.util.Map<?, ?>) runtimes).get(key);
            if (runtime == null) {
                return false;
            }
            java.lang.reflect.Field tasksField = runtime.getClass().getDeclaredField("tasksByCitizen");
            tasksField.setAccessible(true);
            java.util.Map<?, ?> tasks = (java.util.Map<?, ?>) tasksField.get(runtime);
            Object taskRuntime = tasks.get(citizenId);
            if (taskRuntime == null) {
                return false;
            }
            java.lang.reflect.Field taskField = taskRuntime.getClass().getDeclaredField("task");
            taskField.setAccessible(true);
            BuildingTaskData running = (BuildingTaskData) taskField.get(taskRuntime);
            return running != null && taskId.equals(running.taskId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static void handlePause(ServerLevel level, UUID cityId, UUID citizenId) {
        if (BuildTaskTrackedState.isPaused(level, cityId, citizenId)) {
            return;
        }

        BuilderTaskControl.stopRuntime(level, citizenId);
        BuildTaskTrackedState.setPaused(level, cityId, citizenId);
        setCitizenWorkStatus(level, citizenId, CitizenWorkStatus.RESTING,
                "status.simukraft.builder.paused");
    }

    private static void handleResume(ServerLevel level, UUID cityId, UUID citizenId, BuildingTaskData task) {
        if (!BuildTaskTrackedState.isPaused(level, cityId, citizenId)) {
            return;
        }
        BuildTaskTrackedState.setResumed(level, cityId, citizenId);

        BuilderTaskControl.resumeTask(level, task);
        setCitizenWorkStatus(level, citizenId, CitizenWorkStatus.WORKING, "");
        CitizenService.findCitizen(level, citizenId)
                .ifPresent(c -> CitizenWorkplaceMoveService.returnToWorkplace(level, c));
    }

    private static void handleAbort(ServerLevel level, UUID cityId, UUID citizenId, UUID taskId) {
        boolean stoppedRunning = BuilderTaskControl.stopRuntimeIfTask(level, citizenId, taskId);
        BuildingTaskQueueService.removeQueuedByTaskId(level, taskId);
        deleteBuildingTaskByTaskId(level, citizenId, taskId);
        BuildTaskTrackedState.setResumed(level, cityId, citizenId);
        if (stoppedRunning) {
            setCitizenWorkStatus(level, citizenId, CitizenWorkStatus.IDLE, "");
        }
        BuildingTaskQueueService.onTaskFinished(level, cityId, taskId);
    }

    private static void deleteBuildingTaskByTaskId(ServerLevel level, UUID citizenId, UUID taskId) {
        if (level == null || taskId == null || citizenId == null || level.getServer() == null) {
            return;
        }
        BuildingTaskQueueStorage.deleteByTaskId(level, taskId);
    }

    private static void setCitizenWorkStatus(ServerLevel level, UUID citizenId,
                                             CitizenWorkStatus status, String statusLabel) {
        CitizenService.findCitizen(level, citizenId).ifPresent(citizen -> {
            citizen.setWorkStatus(status);
            citizen.setStatusLabel(statusLabel);
            SimuSqliteStorage.saveCitizen(level, citizen.toTag());
        });
    }
}
