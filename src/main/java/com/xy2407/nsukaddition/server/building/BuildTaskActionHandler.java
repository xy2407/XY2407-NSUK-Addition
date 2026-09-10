package com.xy2407.nsukaddition.server.building;

import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import com.xy2407.nsukaddition.server.planning.PlanningPauseState;
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
                              com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket.Action action,
                              boolean plan) {
        if (plan) {
            handlePlanning(level, citizenId, taskId, action);
            SidebarDataCache.refreshAsync(level);
            return;
        }
        BuildingTaskData task = SimuSqliteStorage.loadBuildingTask(level, citizenId);
        // 城市以任务归属为准，避免暂停/恢复状态按玩家所属城市记录、而与侧边栏按任务城市查询不一致(导致首次暂停"回弹")
        UUID cityId = task != null ? task.cityId()
                : CityManager.get(level).getPlayerCity(player.getUUID()).map(CityData::cityId).orElse(null);
        if (cityId == null) return;

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

        if (task == null) return;
        switch (action) {
            case PAUSE -> handlePause(level, cityId, citizenId);
            case RESUME -> handleResume(level, cityId, citizenId, task);
            case ABORT -> {
            }
        }
        SidebarDataCache.refreshAsync(level);
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

        BuildingTaskQueueService.startTaskIfIdle(level, task.withStatus(BuildingTaskStatus.BUILDING));
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

    private static void handlePlanning(ServerLevel level, UUID citizenId, UUID taskId,
                                       com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket.Action action) {
        switch (action) {
            case PAUSE -> planningPause(level, citizenId);
            case RESUME -> planningResume(level, citizenId);
            case ABORT -> planningAbort(level, citizenId);
            case TRACK -> { } // 规划任务无追踪概念
        }
    }

    private static void planningPause(ServerLevel level, UUID citizenId) {
        if (citizenId == null || PlanningPauseState.isPaused(level, citizenId)) {
            return;
        }
        PlanningPauseState.setPaused(level, citizenId);
        setCitizenWorkStatus(level, citizenId, CitizenWorkStatus.RESTING, "");
    }

    private static void planningResume(ServerLevel level, UUID citizenId) {
        if (citizenId == null || !PlanningPauseState.isPaused(level, citizenId)) {
            return;
        }
        PlanningPauseState.setResumed(level, citizenId);
        setCitizenWorkStatus(level, citizenId, CitizenWorkStatus.WORKING, "");
    }

    private static void planningAbort(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        PlanningPauseState.setResumed(level, citizenId);
        PlannerWorkService.cancelTask(level, citizenId);
        setCitizenWorkStatus(level, citizenId, CitizenWorkStatus.IDLE, "");
    }
}
