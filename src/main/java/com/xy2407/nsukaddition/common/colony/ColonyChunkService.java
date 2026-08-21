package com.xy2407.nsukaddition.common.colony;

import com.xy2407.nsukaddition.common.network.colony.ColonyChunkSyncPacket;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * 附属地领地区块购买/放弃的公共校验与执行,供单块与批量网络包复用。
 */
public final class ColonyChunkService {
    private ColonyChunkService() {
    }

    public static boolean buyChunk(ServerLevel level, ServerPlayer player, ColonyData colony, int chunkX, int chunkZ) {
        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos targetChunk = new ChunkPos(chunkX, chunkZ);
        UUID existingOwner = chunkMgr.getChunkOwner(targetChunk.toLong());
        if (existingOwner != null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_POS_ALREADY_CLAIMED));
            return false;
        }
        if (!isAdjacentToColony(level, colony.colonyId(), targetChunk)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_not_adjacent"));
            return false;
        }
        int usedPool = ColonyCreateService.getUsedChunkPool(level, colony.parentCityId());
        int totalPool = ColonyCreateService.getTotalChunkPool(level, colony.parentCityId());
        if (usedPool >= totalPool) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_CHUNK_POOL_EMPTY));
            return false;
        }
        String dimId = level.dimension().location().toString();
        chunkMgr.claimChunk(colony.colonyId(), targetChunk.toLong());
        ColonySqliteStorage.addChunk(level, colony.colonyId(), dimId, chunkX, chunkZ);
        return true;
    }

    public static boolean abandonChunk(ServerLevel level, ServerPlayer player, ColonyData colony, int chunkX, int chunkZ) {
        String dimId = level.dimension().location().toString();
        if (!ColonySqliteStorage.hasChunk(level, colony.colonyId(), dimId, chunkX, chunkZ)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_not_owned"));
            return false;
        }
        int coreChunkX = colony.corePos().getX() >> 4;
        int coreChunkZ = colony.corePos().getZ() >> 4;
        if (chunkX == coreChunkX && chunkZ == coreChunkZ) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_is_core"));
            return false;
        }
        ColonySqliteStorage.removeChunk(level, colony.colonyId(), dimId, chunkX, chunkZ);
        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos targetChunk = new ChunkPos(chunkX, chunkZ);
        UUID owner = chunkMgr.getChunkOwner(targetChunk.toLong());
        if (owner != null && owner.equals(colony.colonyId())) {
            chunkMgr.unclaimChunk(colony.colonyId(), targetChunk.toLong());
        }
        return true;
    }

    public static void broadcastAfterChange(ServerLevel level, ColonyData colony) {
        ColonyChunkSyncPacket.broadcast(level, colony.colonyId());
        CityChunkSyncService.syncToAll(level);
    }

    private static boolean isAdjacentToColony(ServerLevel level, UUID colonyId, ChunkPos target) {
        for (ColonySqliteStorage.ChunkEntry ce : ColonySqliteStorage.loadChunksByColony(level, colonyId)) {
            int dx = Math.abs(ce.x() - target.x);
            int dz = Math.abs(ce.z() - target.z);
            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                return true;
            }
        }
        return false;
    }
}