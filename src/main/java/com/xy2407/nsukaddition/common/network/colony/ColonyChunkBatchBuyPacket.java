package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyChunkService;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
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
 * 附属地中键拖选批量购买领地网络包,服务端逐块校验购买并汇总提示。
 */
@SuppressWarnings("null")
public record ColonyChunkBatchBuyPacket(UUID colonyId, List<ChunkEntry> chunks) implements CustomPacketPayload {

    private static final int MAX_CHUNKS = 256;

    public static final Type<ColonyChunkBatchBuyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_chunk_batch_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyChunkBatchBuyPacket> STREAM_CODEC =
            StreamCodec.of(ColonyChunkBatchBuyPacket::encode, ColonyChunkBatchBuyPacket::decode);

    public ColonyChunkBatchBuyPacket {
        chunks = chunks == null ? List.of() : List.copyOf(chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyChunkBatchBuyPacket p) {
        b.writeUUID(p.colonyId());
        b.writeVarInt(p.chunks().size());
        for (ChunkEntry chunk : p.chunks()) {
            b.writeVarInt(chunk.chunkX());
            b.writeVarInt(chunk.chunkZ());
        }
    }

    public static ColonyChunkBatchBuyPacket decode(RegistryFriendlyByteBuf b) {
        UUID colonyId = b.readUUID();
        int size = b.readVarInt();
        if (size < 0 || size > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid colony batch buy size: " + size);
        }
        List<ChunkEntry> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chunks.add(new ChunkEntry(b.readVarInt(), b.readVarInt()));
        }
        return new ColonyChunkBatchBuyPacket(colonyId, chunks);
    }

    public static void handle(ColonyChunkBatchBuyPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (p.chunks().isEmpty()) {
            return;
        }
        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_NOT_FOUND));
            return;
        }
        int purchased = 0;
        int failed = 0;
        for (ChunkEntry chunk : p.chunks()) {
            if (ColonyChunkService.buyChunk(level, player, colony, chunk.chunkX(), chunk.chunkZ())) {
                purchased++;
            } else {
                failed++;
            }
        }
        if (purchased > 0) {
            ColonyChunkService.broadcastAfterChange(level, colony);
        }
        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.chunk_batch_buy_result", purchased, failed));
    }

    public record ChunkEntry(int chunkX, int chunkZ) {
    }
}
