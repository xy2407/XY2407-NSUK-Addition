package com.xy2407.nsukaddition.server.autorestock;

import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 物流仓库低开销强加载：对仓库箱子区块加 level 33 的 region ticket，常驻且不 tick 生物/方块随机tick，仅保留方块实体可读。 */
public final class WarehouseChunkLoader {

    private static final int LOW_TICK_FREE_LEVEL = 33;
    private static final TicketType<ChunkPos> TYPE = TicketType.create("nsuk_warehouse", java.util.Comparator.comparingLong(ChunkPos::toLong));

    private static final ConcurrentMap<String, Set<Long>> LEVEL_CHUNKS = new ConcurrentHashMap<>();

    private WarehouseChunkLoader() {
    }

    public static void forceLoad(ServerLevel level, LogisticsWarehouseData warehouse) {
        if (level == null || warehouse == null || warehouse.containers() == null) {
            return;
        }
        for (BlockPos chest : warehouse.containers()) {
            if (chest != null) {
                retain(level, new ChunkPos(chest));
            }
        }
    }

    /** refreshAll: 对指定维度下所有物流仓库的箱子区块一次性维持 33 级常加载，服务端低频周期调用。 */
    public static void refreshAll(ServerLevel level) {
        if (level == null) {
            return;
        }
        String dimensionId = level.dimension().location().toString();
        for (LogisticsWarehouseData warehouse : LogisticsManager.get(level).warehouses()) {
            if (warehouse == null || warehouse.containers() == null) {
                continue;
            }
            if (dimensionId.equals(warehouse.dimensionId())) {
                forceLoad(level, warehouse);
            }
        }
    }

    private static void retain(ServerLevel level, ChunkPos chunk) {
        String levelKey = level.dimension().location().toString();
        Set<Long> chunks = LEVEL_CHUNKS.computeIfAbsent(levelKey, k -> ConcurrentHashMap.newKeySet());
        if (chunks.add(chunk.toLong())) {
            level.getChunkSource().addRegionTicket(TYPE, chunk, LOW_TICK_FREE_LEVEL, chunk);
        }
    }

    public static void releaseAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            String levelKey = level.dimension().location().toString();
            Set<Long> chunks = LEVEL_CHUNKS.remove(levelKey);
            if (chunks == null) {
                continue;
            }
            ServerChunkCache cache = level.getChunkSource();
            for (long key : chunks) {
                ChunkPos chunk = new ChunkPos(key);
                cache.removeRegionTicket(TYPE, chunk, LOW_TICK_FREE_LEVEL, chunk);
            }
        }
    }
}