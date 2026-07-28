package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** 城市核心方块迁移服务：校验权限 → 更新数据 → 移除旧方块 → 放置新方块。 */
@SuppressWarnings("null")
public final class CityCoreMoveService {

    private static final ThreadLocal<BlockPos> movingFrom = new ThreadLocal<>();

    private CityCoreMoveService() {}

    public static boolean isMovingFrom(BlockPos pos) {
        BlockPos from = movingFrom.get();
        return from != null && from.equals(pos);
    }

    public enum MoveResult {
        SUCCESS, FAIL_NO_PERMISSION, FAIL_CITY_NOT_FOUND, FAIL_POS_MISMATCH,
        FAIL_SAME_POSITION, FAIL_NEW_POS_OCCUPIED, FAIL_CHUNK_NOT_OWNED
    }

    public static MoveResult executeMove(ServerLevel level, ServerPlayer player, UUID cityId, BlockPos oldCorePos, BlockPos newCorePos) {
        CityManager mgr = CityManager.get(level);
        CityData city = mgr.getCity(cityId).orElse(null);
        if (city == null) return MoveResult.FAIL_CITY_NOT_FOUND;

        if (!city.hasPermission(player.getUUID(), CityPermissionLevel.MAYOR)) {
            return MoveResult.FAIL_NO_PERMISSION;
        }

        if (!city.cityCorePos().equals(oldCorePos)) return MoveResult.FAIL_POS_MISMATCH;
        if (oldCorePos.equals(newCorePos)) return MoveResult.FAIL_SAME_POSITION;

        BlockState newState = level.getBlockState(newCorePos);
        if (!newState.isAir() && !newState.canBeReplaced()) {
            return MoveResult.FAIL_NEW_POS_OCCUPIED;
        }

        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos newChunk = new ChunkPos(newCorePos);
        UUID chunkOwner = chunkMgr.getChunkOwner(newChunk.toLong());
        if (!cityId.equals(chunkOwner)) {
            return MoveResult.FAIL_CHUNK_NOT_OWNED;
        }

        movingFrom.set(oldCorePos);

        try {
            CityCorePosAccessor.setCityCorePos(city, newCorePos.immutable());

            String dimId = level.dimension().location().toString();
            String oldKey = dimId + "@" + oldCorePos.asLong();
            String newKey = dimId + "@" + newCorePos.asLong();
            CityManagerIndexAccessor.updateCorePosIndex(mgr, oldKey, newKey, cityId);

            SimuSqliteStorage.saveCity(level, city.toTag());

            mgr.setDirty();

            level.setBlock(newCorePos, ModBlocks.CITY_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

            level.setBlock(oldCorePos, level.getFluidState(oldCorePos).createLegacyBlock(), Block.UPDATE_ALL);

            CityChunkSyncService.syncToAll(level);
            HudSyncService.syncToCityGroup(level, cityId, true);

            return MoveResult.SUCCESS;
        } finally {
            movingFrom.remove();
        }
    }
}
