package com.xy2407.nsukaddition.server.building;

import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** 建造任务操作处理器，处理暂停、恢复和追踪建造任务的客户端请求。 */
public final class BuildTaskActionHandler {

    private BuildTaskActionHandler() {
    }

    public static void handle(ServerLevel level, ServerPlayer player,
                              UUID citizenId,
                              com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket.Action action) {
        var cityOpt = CityManager.get(level).getPlayerCity(player.getUUID());
        if (cityOpt.isEmpty()) return;
        UUID cityId = cityOpt.get().cityId();

        BuildingTaskData task = SimuSqliteStorage.loadBuildingTask(level, citizenId);
        if (task == null || !cityId.equals(task.cityId())) return;

        switch (action) {
            case PAUSE -> handlePause(level, cityId, citizenId);
            case RESUME -> handleResume(level, cityId, citizenId, task);
            case TRACK -> BuildTaskTrackedState.set(level, cityId, citizenId);
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

    private static void setCitizenWorkStatus(ServerLevel level, UUID citizenId,
                                             CitizenWorkStatus status, String statusLabel) {
        CitizenService.findCitizen(level, citizenId).ifPresent(citizen -> {
            citizen.setWorkStatus(status);
            citizen.setStatusLabel(statusLabel);
            SimuSqliteStorage.saveCitizen(level, citizen.toTag());
        });
    }
}
