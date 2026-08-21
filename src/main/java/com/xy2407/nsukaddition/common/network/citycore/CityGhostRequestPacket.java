package com.xy2407.nsukaddition.common.network.citycore;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.building.BuildingTaskQueueService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 客户端→服务端：请求全部进行中/排队建筑任务的虚影快照。 */
public record CityGhostRequestPacket() implements CustomPacketPayload {

    public static final Type<CityGhostRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "city_ghost_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityGhostRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new CityGhostRequestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CityGhostRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ctx.enqueueWork(() -> {
            List<CityGhostSyncPacket.GhostTaskInfo> infos = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (BuildingTaskData t : SimuSqliteStorage.loadBuildingTasks(level)) {
                BuildingTaskStatus st = BuildingTaskStatus.from(t.status());
                if (st == BuildingTaskStatus.COMPLETED || st == BuildingTaskStatus.INTERRUPTED) {
                    continue;
                }
                if (seen.add(t.taskId())) {
                    infos.add(toInfo(t));
                }
            }
            for (BuildingTaskData t : BuildingTaskQueueService.allQueued(level)) {
                if (seen.add(t.taskId())) {
                    infos.add(toInfo(t));
                }
            }
            ctx.reply(new CityGhostSyncPacket(infos));
        });
    }

    private static CityGhostSyncPacket.GhostTaskInfo toInfo(BuildingTaskData t) {
        return new CityGhostSyncPacket.GhostTaskInfo(t.taskId(), t.category(), t.buildingFileName(),
                t.origin(), t.rotationDegrees(), t.status(), t.dimensionId(), t.buildBoxPos());
    }
}
