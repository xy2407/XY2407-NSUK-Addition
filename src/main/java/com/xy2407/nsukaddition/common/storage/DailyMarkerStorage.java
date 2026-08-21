package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 每日刷新标记的 SQLite 持久化：记录“某城市某类标记已刷新到第几天”，防重启后同日重复生成。 */
@SuppressWarnings("null")
public final class DailyMarkerStorage {

    private static final String TABLE = "daily_markers";

    private DailyMarkerStorage() {
    }

    public static Map<UUID, Long> loadMarkers(ServerLevel level, String kind) {
        Map<UUID, Long> markers = new HashMap<>();
        if (level == null || kind == null || kind.isBlank()) {
            return markers;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return markers;
        }
        try (Connection connection = db.borrowConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT city_id, day FROM " + TABLE + " WHERE kind = ?")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    markers.put(UUID.fromString(rs.getString("city_id")), rs.getLong("day"));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            NsukAddition.LOGGER.error("Failed to load daily markers kind={}", kind, e);
        }
        return markers;
    }

    public static void save(ServerLevel level, UUID cityId, String kind, long day) {
        if (level == null || cityId == null || kind == null || kind.isBlank()) {
            return;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submit(db, TABLE, kind + ":" + cityId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + TABLE + "(city_id, kind, day) VALUES(?, ?, ?) "
                            + "ON CONFLICT(city_id, kind) DO UPDATE SET day = excluded.day")) {
                ps.setString(1, cityId.toString());
                ps.setString(2, kind);
                ps.setLong(3, day);
                ps.executeUpdate();
            }
        });
    }
}
