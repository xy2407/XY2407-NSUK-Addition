package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 村庄城市类型映射存储，记录SimuKraft城市ID与原版村庄类型的对应关系。 */
@SuppressWarnings("null")
public final class VillageCityTypeStorage {

    private static final ConcurrentMap<UUID, String> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static final UUID PRELOAD_KEY = new UUID(0L, 0L);

    private VillageCityTypeStorage() {}

    public static String getVillageType(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) return null;
        String cached = CACHE.get(cityId);
        if (cached != null) return cached;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return null;
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT village_type FROM village_city_type WHERE city_id = ?")) {
            ps.setString(1, cityId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String villageType = rs.getString("village_type");
                    CACHE.put(cityId, villageType);
                    return villageType;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load village city type", e);
        }
        return null;
    }

    public static void preloadAll(ServerLevel level) {
        if (level == null) return;
        if (!LOADING.add(PRELOAD_KEY)) return;
        try {
            NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
            if (db == null) return;
            try (Connection conn = db.openConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT city_id, village_type FROM village_city_type")) {
                while (rs.next()) {
                    try {
                        UUID cityId = UUID.fromString(rs.getString("city_id"));
                        String villageType = rs.getString("village_type");
                        CACHE.put(cityId, villageType);
                    } catch (SQLException | IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            NsukAddition.LOGGER.warn("Failed to preload village city types", e);
        } finally {
            LOADING.remove(PRELOAD_KEY);
        }
    }

    public static void saveVillageType(ServerLevel level, UUID cityId, String villageType) {
        if (level == null || cityId == null || villageType == null) return;
        CACHE.put(cityId, villageType);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submit(db, "village_city_type", "village_city_type:" + cityId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO village_city_type(city_id, village_type) VALUES(?, ?)")) {
                ps.setString(1, cityId.toString());
                ps.setString(2, villageType);
                ps.executeUpdate();
            }
        });
    }

    public static void clearCache() {
        CACHE.clear();
        LOADING.clear();
    }
}