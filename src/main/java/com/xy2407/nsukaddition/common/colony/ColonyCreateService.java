package com.xy2407.nsukaddition.common.colony;

import com.xy2407.nsukaddition.common.city.CityDataService;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkSyncPacket;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** 附属领地创建与销毁服务，处理放置/移除核心方块时的领地生命周期。 */
@SuppressWarnings("null")
public final class ColonyCreateService {

    private ColonyCreateService() {}

    public enum CreateResult {
        SUCCESS, FAIL_NO_CITY, FAIL_LEVEL_TOO_LOW, FAIL_COLONY_LIMIT,
        FAIL_CHUNK_POOL_EMPTY, FAIL_POS_ALREADY_CLAIMED, FAIL_UNKNOWN
    }

    public static boolean hasColonyAt(ServerLevel level, BlockPos pos) {
        String dimId = level.dimension().location().toString();
        return ColonySqliteStorage.loadColonyByCorePos(level, dimId, pos.asLong()) != null;
    }

    public static CreateResult createColony(ServerLevel level, ServerPlayer player, BlockPos corePos) {
        return createColony(level, player, corePos, null);
    }

    public static CreateResult createColony(ServerLevel level, ServerPlayer player, BlockPos corePos, String customName) {
        CityData city = findPlayerCity(level, player);
        if (city == null) return CreateResult.FAIL_NO_CITY;

        if (!city.hasPermission(player.getUUID(), CityPermissionLevel.MAYOR)) {
            return CreateResult.FAIL_NO_CITY;
        }

        CityLevel cityLevel = CityLevel.fromLevel(city.cityLevel());
        if (cityLevel.level() < CityLevel.TOWN.level()) return CreateResult.FAIL_LEVEL_TOO_LOW;

        List<ColonyData> existing = ColonySqliteStorage.loadColoniesByParentCity(level, city.cityId());
        int maxColonies = ColonyConstants.maxColonies(cityLevel.level());
        if (existing.size() >= maxColonies) return CreateResult.FAIL_COLONY_LIMIT;

        int usedChunks = ColonySqliteStorage.countChunksByParentCity(level, city.cityId());
        int totalPool = ColonyConstants.totalChunkPool(cityLevel.level());
        if (usedChunks >= totalPool) return CreateResult.FAIL_CHUNK_POOL_EMPTY;

        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos coreChunk = new ChunkPos(corePos);
        UUID existingOwner = chunkMgr.getChunkOwner(coreChunk.toLong());
        if (existingOwner != null) return CreateResult.FAIL_POS_ALREADY_CLAIMED;

        String dimId = level.dimension().location().toString();
        UUID colonyId = UUID.randomUUID();
        String colonyName = (customName != null && !customName.isBlank()) ? customName : "附属地-" + (existing.size() + 1);
        ColonyData colony = new ColonyData(
                colonyId, city.cityId(), colonyName,
                corePos.immutable(), dimId, System.currentTimeMillis()
        );
        ColonySqliteStorage.saveColony(level, colony);

        int radius = ColonyConstants.INITIAL_CLAIM_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(coreChunk.x + dx, coreChunk.z + dz);
                if (chunkMgr.getChunkOwner(cp.toLong()) == null) {
                    chunkMgr.claimChunk(colonyId, cp.toLong());
                    ColonySqliteStorage.addChunk(level, colonyId, dimId, cp.x, cp.z);
                }
            }
        }

        ColonyChunkSyncPacket.broadcast(level, colonyId);
        CityChunkSyncService.syncToAll(level);

        return CreateResult.SUCCESS;
    }

    public static void onCoreRemoved(ServerLevel level, BlockPos pos) {
        String dimId = level.dimension().location().toString();
        ColonyData colony = ColonySqliteStorage.loadColonyByCorePos(level, dimId, pos.asLong());
        if (colony == null) return;

        CityData parentCity = CityManager.get(level).getCity(colony.parentCityId()).orElse(null);
        BlockPos cityCorePos = parentCity != null ? parentCity.cityCorePos() : null;

        List<UUID> citizenIds = ColonySqliteStorage.loadCitizensByColony(level, colony.colonyId());
        for (UUID citizenUuid : citizenIds) {
            CitizenService.clearEmployment(level, citizenUuid);
            CitizenService.setHome(level, citizenUuid, null);
            ColonySqliteStorage.removeCitizen(level, citizenUuid);
            if (cityCorePos != null) {
                teleportCitizenToCityCore(level, citizenUuid, cityCorePos);
            }
        }

        CityChunkManager chunkMgr = CityChunkManager.get(level);
        List<ColonySqliteStorage.ChunkEntry> chunks = ColonySqliteStorage.loadChunksByColony(level, colony.colonyId());
        for (ColonySqliteStorage.ChunkEntry chunk : chunks) {
            long chunkLong = new ChunkPos(chunk.x(), chunk.z()).toLong();
            chunkMgr.unclaimChunk(colony.colonyId(), chunkLong);
        }

        ColonySqliteStorage.deleteColony(level, colony.colonyId());

        ColonyChunkSyncPacket.broadcastRemoval(level, colony.colonyId());
        CityChunkSyncService.syncToAll(level);
    }

    private static void teleportCitizenToCityCore(ServerLevel level, UUID citizenUuid, BlockPos cityCorePos) {
        CitizenTeleportService.teleportCitizen(level, citizenUuid,
                new Vec3(cityCorePos.getX() + 0.5, cityCorePos.getY(), cityCorePos.getZ() + 0.5));
    }

    private static CityData findPlayerCity(ServerLevel level, ServerPlayer player) {
        return CityService.findManagedPlayerCity(level, player.getUUID()).orElse(null);
    }

    public static int getUsedChunkPool(ServerLevel level, UUID parentCityId) {
        return ColonySqliteStorage.countChunksByParentCity(level, parentCityId);
    }

    public static int getTotalChunkPool(ServerLevel level, UUID parentCityId) {
        CityData city = CityDataService.getCity(level, parentCityId);
        if (city == null) return 0;
        return ColonyConstants.totalChunkPool(city.cityLevel());
    }
}
