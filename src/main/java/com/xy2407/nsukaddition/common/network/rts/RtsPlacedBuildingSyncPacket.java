package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务器响应：该城市已放置建筑列表(界限/结构引用/旋转) + 城市领地区块列表，供客户端渲染建筑界限与城市领地边界。
 */
public record RtsPlacedBuildingSyncPacket(List<Entry> buildings, List<Long> cityChunks) implements CustomPacketPayload {

    public static final Type<RtsPlacedBuildingSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_placed_building_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsPlacedBuildingSyncPacket> STREAM_CODEC =
            StreamCodec.of(RtsPlacedBuildingSyncPacket::encode, RtsPlacedBuildingSyncPacket::decode);

    public record Entry(UUID buildingId, String category, String buildingFileName,
                        BlockPos minPos, BlockPos maxPos, BlockPos origin, int rotation) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RtsPlacedBuildingSyncPacket p) {
        b.writeVarInt(p.buildings().size());
        for (Entry e : p.buildings()) {
            b.writeUUID(e.buildingId());
            b.writeUtf(e.category() != null ? e.category() : "", 64);
            b.writeUtf(e.buildingFileName() != null ? e.buildingFileName() : "", 128);
            b.writeBlockPos(e.minPos());
            b.writeBlockPos(e.maxPos());
            b.writeBlockPos(e.origin());
            b.writeVarInt(e.rotation());
        }
        b.writeVarInt(p.cityChunks().size());
        for (long chunk : p.cityChunks()) {
            b.writeLong(chunk);
        }
    }

    public static RtsPlacedBuildingSyncPacket decode(RegistryFriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid placed building sync size: " + size);
        }
        List<Entry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = b.readUUID();
            String category = b.readUtf(64);
            String file = b.readUtf(128);
            BlockPos min = b.readBlockPos();
            BlockPos max = b.readBlockPos();
            BlockPos origin = b.readBlockPos();
            int rotation = b.readVarInt();
            list.add(new Entry(id, category, file, min, max, origin, rotation));
        }
        int chunkSize = b.readVarInt();
        if (chunkSize < 0 || chunkSize > 65536) {
            throw new IllegalArgumentException("Invalid city chunks sync size: " + chunkSize);
        }
        List<Long> chunks = new ArrayList<>(chunkSize);
        for (int i = 0; i < chunkSize; i++) {
            chunks.add(b.readLong());
        }
        return new RtsPlacedBuildingSyncPacket(list, chunks);
    }

    public static void handle(RtsPlacedBuildingSyncPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.xy2407.nsukaddition.common.network.clientbound.RtsPlacedBuildingSyncBridge.dispatch(p.buildings(), p.cityChunks()));
    }
}
