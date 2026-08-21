package com.xy2407.nsukaddition.common.network.citycore;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.CityGhostSyncBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 服务端→客户端：全部建筑任务虚影快照(结构/位置/旋转)。 */
public record CityGhostSyncPacket(List<GhostTaskInfo> tasks) implements CustomPacketPayload {

    public record GhostTaskInfo(UUID taskId, String category, String buildingFileName, BlockPos origin,
                                int rotationDegrees, String status, String dimensionId, BlockPos buildBoxPos) {
    }

    public static final Type<CityGhostSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "city_ghost_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityGhostSyncPacket> STREAM_CODEC =
            StreamCodec.of(CityGhostSyncPacket::encode, CityGhostSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, CityGhostSyncPacket p) {
        b.writeVarInt(p.tasks.size());
        for (GhostTaskInfo t : p.tasks) {
            b.writeUUID(t.taskId());
            b.writeUtf(t.category(), 64);
            b.writeUtf(t.buildingFileName(), 256);
            b.writeBlockPos(t.origin());
            b.writeVarInt(t.rotationDegrees());
            b.writeUtf(t.status(), 64);
            b.writeUtf(t.dimensionId(), 64);
            b.writeBlockPos(t.buildBoxPos());
        }
    }

    public static CityGhostSyncPacket decode(RegistryFriendlyByteBuf b) {
        int n = b.readVarInt();
        List<GhostTaskInfo> tasks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tasks.add(new GhostTaskInfo(b.readUUID(), b.readUtf(64), b.readUtf(256), b.readBlockPos(),
                    b.readVarInt(), b.readUtf(64), b.readUtf(64), b.readBlockPos()));
        }
        return new CityGhostSyncPacket(tasks);
    }

    public static void handle(CityGhostSyncPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CityGhostSyncBridge.handle(p.tasks()));
    }
}
