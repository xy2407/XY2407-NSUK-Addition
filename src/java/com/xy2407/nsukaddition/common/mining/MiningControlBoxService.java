package com.xy2407.nsukaddition.common.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.block.entity.MiningControlBoxBlockEntity;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxViewUpdatePacket;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 挖矿控制盒业务逻辑服务，管理运行状态、工人与容器查找。 */
@SuppressWarnings("null")
public final class MiningControlBoxService {

    private MiningControlBoxService() {}

    public static MiningControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        MiningBoxManager manager = MiningBoxManager.get(level);
        MiningBoxData data = manager.getOrCreate(boxPos);
        CitizenData worker = findAssignedWorker(level, boxPos);

        String statusKey = data.statusKey().isBlank() ? MiningConstants.STATUS_IDLE : data.statusKey();
        if (worker == null && data.running()) {
            statusKey = MiningConstants.STATUS_NO_WORKER;
        }

        return new MiningControlBoxView(
                boxPos.immutable(),
                worker != null,
                worker != null ? worker.name() : "",
                data.currentYLevel(),
                data.running(),
                data.workTicks(),
                MiningConstants.TICKS_PER_LAYER,
                statusKey,
                data.statusText()
        );
    }

    public static boolean toggleRunning(ServerLevel level, BlockPos boxPos) {
        MiningBoxManager manager = MiningBoxManager.get(level);
        MiningBoxData data = manager.getOrCreate(boxPos);

        if (data.running()) {
            data.setRunning(false);
            data.setWorkTicks(0);
            data.setStatusKey(MiningConstants.STATUS_PAUSED);
            data.setStatusText("");
            manager.persist(data);
            broadcastViewUpdate(level, boxPos);
            return true;
        }

        CitizenData worker = findAssignedWorker(level, boxPos);
        if (worker == null) {
            data.setStatusKey(MiningConstants.STATUS_NO_WORKER);
            data.setStatusText("");
            manager.persist(data);
            broadcastViewUpdate(level, boxPos);
            return false;
        }
        if (data.currentYLevel() <= MiningConstants.MIN_DEPTH) {
            data.setStatusKey(MiningConstants.STATUS_MAX_DEPTH);
            data.setStatusText("");
            manager.persist(data);
            broadcastViewUpdate(level, boxPos);
            return false;
        }
        if (!hasUsablePickaxe(level, boxPos)) {
            data.setStatusKey(MiningConstants.STATUS_NO_PICKAXE);
            data.setStatusText("");
            manager.persist(data);
            broadcastViewUpdate(level, boxPos);
            return false;
        }

        data.setRunning(true);
        data.setWorkTicks(0);
        data.setStatusKey(MiningConstants.STATUS_RUNNING);
        data.setStatusText("");
        manager.persist(data);
        broadcastViewUpdate(level, boxPos);
        return true;
    }

    public static boolean hasUsablePickaxe(ServerLevel level, BlockPos boxPos) {
        BlockEntity be = level.getBlockEntity(boxPos);
        return be instanceof MiningControlBoxBlockEntity box && box.hasPickaxe();
    }

    public static void broadcastViewUpdate(ServerLevel level, BlockPos boxPos) {
        MiningControlBoxView view = buildView(level, boxPos);
        MiningControlBoxViewUpdatePacket packet = MiningControlBoxViewUpdatePacket.from(view);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(boxPos, 16.0D)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public static void fireWorker(ServerLevel level, BlockPos boxPos) {
        CitizenData worker = findAssignedWorker(level, boxPos);
        if (worker != null) {
            CitizenJobVisualService.clearMainHandOverride(worker.uuid());
        }
        MiningBoxManager manager = MiningBoxManager.get(level);
        MiningBoxData data = manager.getOrCreate(boxPos);
        data.setRunning(false);
        data.setWorkTicks(0);
        data.setStatusKey(MiningConstants.STATUS_WORKER_FIRED);
        data.setStatusText("");
        manager.persist(data);
        broadcastViewUpdate(level, boxPos);

        CitizenEmploymentService.fireAssigned(
                level,
                CitizenEmploymentService.workplaceId(MiningConstants.HIRE_SOURCE_TYPE, MiningConstants.HIRE_ROLE, boxPos),
                MiningConstants.HIRE_SOURCE_TYPE,
                MiningConstants.HIRE_ROLE,
                boxPos,
                "mining_fired");
    }

    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) return;
        fireWorker(level, boxPos);
        com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig.remove(level, boxPos);
        MiningBoxManager.get(level).remove(boxPos);
    }

    public static void interrupt(ServerLevel level, UUID citizenId, String reason) {
        if (level == null || citizenId == null) return;
        for (MiningBoxData data : MiningBoxManager.get(level).all()) {
            UUID assigned = CitizenEmploymentService.findAssigned(level,
                    MiningConstants.HIRE_SOURCE_TYPE, MiningConstants.HIRE_ROLE, data.boxPos())
                    .map(CitizenData::uuid).orElse(null);
            if (!citizenId.equals(assigned)) continue;
            data.setRunning(false);
            data.setWorkTicks(0);
            data.setStatusKey(MiningConstants.STATUS_INTERRUPTED);
            data.setStatusText(reason != null ? reason : "");
            MiningBoxManager.get(level).persist(data);
        }
    }

    public static boolean isBoxBlockValid(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MiningControlBoxBlock;
    }

    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level,
                MiningConstants.HIRE_SOURCE_TYPE, MiningConstants.HIRE_ROLE, boxPos).orElse(null);
    }

    public static BlockPos resolveContainerPos(ServerLevel level, BlockPos boxPos) {
        return findNearestContainer(level, boxPos, MiningConstants.CONTAINER_SEARCH_RADIUS);
    }

    private static BlockPos findNearestContainer(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (level.isLoaded(check) && level.getBlockEntity(check) instanceof net.minecraft.world.Container) {
                        double dist = center.distSqr(check);
                        if (dist < minDist) {
                            minDist = dist;
                            nearest = check;
                        }
                    }
                }
            }
        }
        return nearest;
    }
}
