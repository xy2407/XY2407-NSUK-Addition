package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.common.city.ImmigrantData;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 移民审批队列 SQLite 存储：
 * 持久化 town_immigration 表中的待审批移民（PENDING），服务器重启后恢复审批队列，
 * 避免重启后待审批移民 NPC 变为"幽灵"（cityId 为空、无审批入口）。
 */
@SuppressWarnings("null")
public final class ImmigrationSqliteStorage {

    private ImmigrationSqliteStorage() {
    }

    public static List<ImmigrantData> loadAll(ServerLevel level) {
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return List.of();
        List<ImmigrantData> result = new ArrayList<>();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT request_id, city_id, citizen_id, name, grant_funds, created_day "
                             + "FROM town_immigration")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID requestId = UUID.fromString(rs.getString("request_id"));
                    UUID cityId = UUID.fromString(rs.getString("city_id"));
                    UUID citizenId = UUID.fromString(rs.getString("citizen_id"));
                    result.add(new ImmigrantData(
                            requestId, cityId, citizenId,
                            rs.getString("name"),
                            rs.getDouble("grant_funds"),
                            rs.getLong("created_day")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load immigration requests", e);
        }
        return result;
    }

    public static java.util.Map<UUID, Vec3> loadSpawnPositions(ServerLevel level) {
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        java.util.Map<UUID, Vec3> result = new java.util.HashMap<>();
        if (db == null) return result;
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT citizen_id, spawn_pos_x, spawn_pos_y, spawn_pos_z FROM town_immigration")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID citizenId = UUID.fromString(rs.getString("citizen_id"));
                    result.put(citizenId, new Vec3(
                            rs.getInt("spawn_pos_x"), rs.getInt("spawn_pos_y"), rs.getInt("spawn_pos_z")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load immigration spawn positions", e);
        }
        return result;
    }

    public static void save(ServerLevel level, ImmigrantData immigrant, Vec3 spawnPos) {
        if (immigrant == null) return;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submit(db, "town_immigration",
                "immigration:" + immigrant.requestId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO town_immigration(request_id, city_id, citizen_id, name, grant_funds, created_day, "
                            + "spawn_pos_x, spawn_pos_y, spawn_pos_z) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(request_id) DO UPDATE SET "
                            + "city_id = excluded.city_id, citizen_id = excluded.citizen_id, name = excluded.name, "
                            + "grant_funds = excluded.grant_funds, created_day = excluded.created_day, "
                            + "spawn_pos_x = excluded.spawn_pos_x, spawn_pos_y = excluded.spawn_pos_y, "
                            + "spawn_pos_z = excluded.spawn_pos_z")) {
                ps.setString(1, immigrant.requestId().toString());
                ps.setString(2, immigrant.cityId().toString());
                ps.setString(3, immigrant.citizenId().toString());
                ps.setString(4, immigrant.name() == null ? "" : immigrant.name());
                ps.setDouble(5, immigrant.grantFunds());
                ps.setLong(6, immigrant.createdDay());
                ps.setInt(7, spawnPos != null ? (int) Math.floor(spawnPos.x) : 0);
                ps.setInt(8, spawnPos != null ? (int) Math.floor(spawnPos.y) : 0);
                ps.setInt(9, spawnPos != null ? (int) Math.floor(spawnPos.z) : 0);
                ps.executeUpdate();
            }
        });
    }

    public static void delete(ServerLevel level, UUID requestId) {
        if (requestId == null) return;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "town_immigration",
                "immigration:" + requestId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM town_immigration WHERE request_id = ?")) {
                ps.setString(1, requestId.toString());
                ps.executeUpdate();
            }
        });
    }

    public static void deleteAllForCity(ServerLevel level, UUID cityId) {
        if (cityId == null) return;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "town_immigration",
                "immigration:city:" + cityId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM town_immigration WHERE city_id = ?")) {
                ps.setString(1, cityId.toString());
                ps.executeUpdate();
            }
        });
    }
}
