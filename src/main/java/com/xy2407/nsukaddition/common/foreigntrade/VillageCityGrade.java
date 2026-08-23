package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 村庄城市等级：按所占区块数划分聚落/村庄/城镇/城邦，并持久化到 SQLite。 */
@SuppressWarnings("null")
public final class VillageCityGrade {

    public static final String HAMLET = "hamlet";
    public static final String VILLAGE = "village";
    public static final String TOWN = "town";
    public static final String CITY_STATE = "city_state";

    public static final int HAMLET_MAX = 12;
    public static final int VILLAGE_MAX = 36;
    public static final int TOWN_MAX = 72;

    private static final ConcurrentMap<UUID, Integer> CACHE = new ConcurrentHashMap<>();

    private VillageCityGrade() {
    }

    public static String gradeForChunks(int chunkCount) {
        if (chunkCount <= HAMLET_MAX) return HAMLET;
        if (chunkCount <= VILLAGE_MAX) return VILLAGE;
        if (chunkCount <= TOWN_MAX) return TOWN;
        return CITY_STATE;
    }

    public static String displayName(String grade) {
        if (grade == null) return "";
        return switch (grade) {
            case HAMLET -> "聚落";
            case VILLAGE -> "村庄";
            case TOWN -> "城镇";
            default -> "城邦";
        };
    }

    public static String displayNameFor(int chunkCount) {
        return displayName(gradeForChunks(chunkCount));
    }

    public static int chunkCount(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) return 0;
        Integer cached = CACHE.get(cityId);
        if (cached != null) return cached;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return 0;
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT chunk_count FROM village_city_grade WHERE city_id = ?")) {
            ps.setString(1, cityId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("chunk_count");
                    CACHE.put(cityId, count);
                    return count;
                }
            }
        } catch (SQLException e) {
            NsukAddition.LOGGER.warn("Failed to load village city grade for {}", cityId, e);
        }
        return 0;
    }

    public static void save(ServerLevel level, UUID cityId, int chunkCount) {
        if (level == null || cityId == null) return;
        CACHE.put(cityId, chunkCount);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submit(db, "village_city_grade", "village_city_grade:" + cityId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO village_city_grade(city_id, grade, chunk_count) VALUES(?, ?, ?)")) {
                ps.setString(1, cityId.toString());
                ps.setString(2, gradeForChunks(chunkCount));
                ps.setInt(3, chunkCount);
                ps.executeUpdate();
            }
        });
    }

    public static void clearCache() {
        CACHE.clear();
    }
}