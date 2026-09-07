package com.xy2407.nsukaddition.server.autorestock;

import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 物流仓库强加载：为启用了自动补货/入库的仓库，对其绑定箱子所在区块加 FORCED 区域 ticket，
 * 保证远处(含殖民地)的仓库箱子在补货操作时可读到/写入，避免物品丢失。服务端关闭时统一释放。
 */
public final class WarehouseChunkLoader {

    private static final TicketType<ChunkPos> TYPE = TicketType.create("nsuk_warehouse", java.util.Comparator.comparingLong(ChunkPos::toLong));

    private WarehouseChunkLoader() {
    }

    /** 记录的维度 → 区块 → 引用数。 */
    private static final ConcurrentMap<String, ConcurrentMap<Long, AtomicInteger>> LEVEL_CHUNK_REFS =
            new ConcurrentHashMap<>();

    /** forceLoad: 对单个仓库的箱子各自所在区块加强加载 ticket（引用数+1）。 */
    public static void forceLoad(ServerLevel level, LogisticsWarehouseData warehouse) {
        if (level == null || warehouse == null || warehouse.containers() == null) {
            return;
        }
        for (BlockPos chest : warehouse.containers()) {
            if (chest == null) {
                continue;
            }
            retain(level, new ChunkPos(chest));
        }
    }

    /** retain: 保留一个区块的FORCED ticket（引用数+1），首次时真正加ticket。 */
    private static void retain(ServerLevel level, ChunkPos chunk) {
        String levelKey = level.dimension().location().toString();
        ConcurrentMap<Long, AtomicInteger> refs = LEVEL_CHUNK_REFS.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>());
        long key = chunk.toLong();
        refs.compute(key, (ignored, counter) -> {
            if (counter == null) {
                level.getChunkSource().addRegionTicket(TYPE, chunk, 0, chunk);
                return new AtomicInteger(1);
            }
            counter.incrementAndGet();
            return counter;
        });
    }

    /** releaseAll: 服务端关闭/卸载时释放本模组所有仓库强加载 ticket。 */
    public static void releaseAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            releaseLevel(level);
        }
        LEVEL_CHUNK_REFS.clear();
    }

    private static void releaseLevel(ServerLevel level) {
        String levelKey = level.dimension().location().toString();
        ConcurrentMap<Long, AtomicInteger> refs = LEVEL_CHUNK_REFS.remove(levelKey);
        if (refs == null) {
            return;
        }
        ServerChunkCache cache = level.getChunkSource();
        for (long key : refs.keySet()) {
            ChunkPos chunk = new ChunkPos(key);
            cache.removeRegionTicket(TYPE, chunk, 0, chunk);
        }
    }
}