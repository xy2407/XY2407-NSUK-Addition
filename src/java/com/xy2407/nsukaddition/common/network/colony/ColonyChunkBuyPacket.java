package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyCreateService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 附属地区块购买网络包，玩家请求购买一个区块扩展附属地领地。 */
@SuppressWarnings("null")
public record ColonyChunkBuyPacket(UUID colonyId, int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final Type<ColonyChunkBuyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_chunk_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyChunkBuyPacket> STREAM_CODEC =
            StreamCodec.of(ColonyChunkBuyPacket::encode, ColonyChunkBuyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyChunkBuyPacket p) {
        b.writeUUID(p.colonyId());
        b.writeVarInt(p.chunkX());
        b.writeVarInt(p.chunkZ());
    }

    public static ColonyChunkBuyPacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyChunkBuyPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(ColonyChunkBuyPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_NOT_FOUND));
            return;
        }

        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos targetChunk = new ChunkPos(p.chunkX(), p.chunkZ());
        UUID existingOwner = chunkMgr.getChunkOwner(targetChunk.toLong());
        if (existingOwner != null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_POS_ALREADY_CLAIMED));
            return;
        }

        if (!isAdjacentToColony(level, colony.colonyId(), targetChunk)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_not_adjacent"));
            return;
        }

        int usedPool = ColonyCreateService.getUsedChunkPool(level, colony.parentCityId());
        int totalPool = ColonyCreateService.getTotalChunkPool(level, colony.parentCityId());
        if (usedPool >= totalPool) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_CHUNK_POOL_EMPTY));
            return;
        }

        String dimId = level.dimension().location().toString();
        chunkMgr.claimChunk(colony.colonyId(), targetChunk.toLong());
        ColonySqliteStorage.addChunk(level, colony.colonyId(), dimId, p.chunkX(), p.chunkZ());

        ColonyChunkSyncPacket.broadcast(level, colony.colonyId());
        CityChunkSyncService.syncToAll(level);

        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.chunk_buy_success", p.chunkX(), p.chunkZ()));
    }

    private static boolean isAdjacentToColony(ServerLevel level, UUID colonyId, ChunkPos target) {
        for (ColonySqliteStorage.ChunkEntry ce : ColonySqliteStorage.loadChunksByColony(level, colonyId)) {
            int dx = Math.abs(ce.x() - target.x);
            int dz = Math.abs(ce.z() - target.z);
            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) return true;
        }
        return false;
    }
}
