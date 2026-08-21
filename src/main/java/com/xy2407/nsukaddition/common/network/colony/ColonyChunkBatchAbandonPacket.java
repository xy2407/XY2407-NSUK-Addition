package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyChunkService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 附属地中键拖选批量放弃领地网络包,服务端逐块校验放弃并汇总提示。
 */
@SuppressWarnings("null")
public record ColonyChunkBatchAbandonPacket(UUID colonyId, List<ChunkEntry> chunks) implements CustomPacketPayload {

    private static final int MAX_CHUNKS = 256;

    public static final Type<ColonyChunkBatchAbandonPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_chunk_batch_abandon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyChunkBatchAbandonPacket> STREAM_CODEC =
            StreamCodec.of(ColonyChunkBatchAbandonPacket::encode, ColonyChunkBatchAbandonPacket::decode);

    public ColonyChunkBatchAbandonPacket {
        chunks = chunks == null ? List.of() : List.copyOf(chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyChunkBatchAbandonPacket p) {
        b.writeUUID(p.colonyId());
        b.writeVarInt(p.chunks().size());
        for (ChunkEntry chunk : p.chunks()) {
            b.writeVarInt(chunk.chunkX());
            b.writeVarInt(chunk.chunkZ());
        }
    }

    public static ColonyChunkBatchAbandonPacket decode(RegistryFriendlyByteBuf b) {
        UUID colonyId = b.readUUID();
        int size = b.readVarInt();
        if (size < 0 || size > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid colony batch abandon size: " + size);
        }
        List<ChunkEntry> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chunks.add(new ChunkEntry(b.readVarInt(), b.readVarInt()));
        }
        return new ColonyChunkBatchAbandonPacket(colonyId, chunks);
    }

    public static void handle(ColonyChunkBatchAbandonPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (p.chunks().isEmpty()) {
            return;
        }
        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.not_found"));
            return;
        }
        int abandoned = 0;
        int failed = 0;
        for (ChunkEntry chunk : p.chunks()) {
            if (ColonyChunkService.abandonChunk(level, player, colony, chunk.chunkX(), chunk.chunkZ())) {
                abandoned++;
            } else {
                failed++;
            }
        }
        if (abandoned > 0) {
            ColonyChunkService.broadcastAfterChange(level, colony);
        }
        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.chunk_batch_abandon_result", abandoned, failed));
    }

    public record ChunkEntry(int chunkX, int chunkZ) {
    }
}
