package com.xy2407.nsukaddition.common.city;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 城市建筑统计的 SQLite 持久化 + 内存缓存。
 * 建筑统计几乎不变化，未加载区块更是恒定，故不再每 tick 全量重算：
 * 首次/失效时用 collect() 重算并落库，之后一直读缓存，仅在处理变更或 TTL 到期时刷新一次。
 */
public final class CityBuildingStatsStore {

    private static final String TABLE = "city_building_stats";

    private static final long TTL_TICKS = 60L;

    private static final ConcurrentMap<UUID, CachedStats> CACHE = new ConcurrentHashMap<>();

    private static final java.util.Set<UUID> DIRTY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private CityBuildingStatsStore() {
    }

    private record CachedStats(long tick, CityBuildingStats stats) {
    }

    public static CityBuildingStats get(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return new CityBuildingStats(0, 0, 0, 0, 0, 0);
        }
        long now = level.getGameTime();
        boolean dirty = DIRTY.remove(cityId);
        CachedStats cached = CACHE.get(cityId);
        if (cached != null && !dirty && now - cached.tick < TTL_TICKS) {
            return cached.stats;
        }

        if (cached == null && !dirty) {
            // 服务器重启后首次访问：读一次 DB 还原持久化历史值，无需重算，也不阻塞后续热路径。
            CityBuildingStats persisted = load(level.getServer(), cityId);
            if (persisted != null) {
                CACHE.put(cityId, new CachedStats(now, persisted));
                return persisted;
            }
        }

        // 缓存过期或事件失效（dirty）时：用权威 collect() 重算（已按 isLoaded 跳过未加载区块、不强加载）并落库。
        CityBuildingStats stats = CityBuildingStats.collect(level, cityId);
        CACHE.put(cityId, new CachedStats(now, stats));
        save(level.getServer(), cityId, stats);
        return stats;
    }

    /** 让指定城市的下一次 get 立即重算并刷新 DB，用于建筑放置/拆除等事件驱动的即时更新。 */
    public static void invalidate(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return;
        }
        CACHE.remove(cityId);
        DIRTY.add(cityId);
    }

    /** 城市删除时清空其建筑统计(缓存+DB)，避免旧城市的计数残留/串城。 */
    public static void delete(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return;
        }
        CACHE.remove(cityId);
        DIRTY.remove(cityId);
        NsukSqliteDatabase db = level != null && level.getServer() != null ? NsukSqliteDatabase.get(level.getServer()) : null;
        if (db == null) {
            return;
        }
        db.callSync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + TABLE + " WHERE city_id = ?")) {
                ps.setString(1, cityId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public static void clearCache() {
        CACHE.clear();
        DIRTY.clear();
    }

    private static CityBuildingStats load(MinecraftServer server, UUID cityId) {
        NsukSqliteDatabase db = server != null ? NsukSqliteDatabase.get(server) : null;
        if (db == null) {
            return null;
        }
        return db.callSync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT farm, ranch, restaurant, factory, mine, housing FROM " + TABLE + " WHERE city_id = ?")) {
                ps.setString(1, cityId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new CityBuildingStats(
                            rs.getInt("farm"), rs.getInt("ranch"), rs.getInt("restaurant"),
                            rs.getInt("factory"), rs.getInt("mine"), rs.getInt("housing"));
                }
            }
        });
    }

    private static void save(MinecraftServer server, UUID cityId, CityBuildingStats stats) {
        NsukSqliteDatabase db = server != null ? NsukSqliteDatabase.get(server) : null;
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submit(db, TABLE, "city_stats:" + cityId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE + "(city_id, farm, ranch, restaurant, factory, mine, housing, updated_at) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(city_id) DO UPDATE SET "
                            + "farm = excluded.farm, ranch = excluded.ranch, restaurant = excluded.restaurant, "
                            + "factory = excluded.factory, mine = excluded.mine, housing = excluded.housing, "
                            + "updated_at = excluded.updated_at")) {
                ps.setString(1, cityId.toString());
                ps.setInt(2, stats.farmCount());
                ps.setInt(3, stats.ranchCount());
                ps.setInt(4, stats.restaurantCount());
                ps.setInt(5, stats.factoryCount());
                ps.setInt(6, stats.mineCount());
                ps.setInt(7, stats.housingCount());
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }
}