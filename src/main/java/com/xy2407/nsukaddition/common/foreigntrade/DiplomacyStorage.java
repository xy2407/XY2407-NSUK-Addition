package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 外交关系SQLite存储，记录玩家与村庄的建交关系，带内存缓存。 */
@SuppressWarnings("null")
public final class DiplomacyStorage {

    private static final ConcurrentHashMap<UUID, List<DiplomacyRelation>> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();

    private DiplomacyStorage() {}

    public record DiplomacyRelation(String villageType, int posX, int posZ, String cityId, String cityName, long establishedAt) {}

    public static List<DiplomacyRelation> loadRelations(ServerLevel level, UUID playerUuid) {
        if (level == null || playerUuid == null) return new ArrayList<>();
        List<DiplomacyRelation> cached = CACHE.get(playerUuid);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        List<DiplomacyRelation> list = queryRelations(level, playerUuid);
        CACHE.put(playerUuid, new CopyOnWriteArrayList<>(list));
        return list;
    }

    public static boolean hasRelation(ServerLevel level, UUID playerUuid, int posX, int posZ) {
        if (level == null || playerUuid == null) return false;
        List<DiplomacyRelation> cached = CACHE.get(playerUuid);
        if (cached != null) {
            for (DiplomacyRelation r : cached) {
                if (r.posX() == posX && r.posZ() == posZ) return true;
            }
            return false;
        }
        List<DiplomacyRelation> list = loadRelations(level, playerUuid);
        for (DiplomacyRelation r : list) {
            if (r.posX() == posX && r.posZ() == posZ) return true;
        }
        return false;
    }

    public static void preloadRelations(ServerLevel level, UUID playerUuid) {
        if (level == null || playerUuid == null) return;
        if (CACHE.containsKey(playerUuid)) return;
        if (!LOADING.add(playerUuid)) return;
        NsukWriteExecutor.submit(() -> {
            try {
                List<DiplomacyRelation> list = queryRelations(level, playerUuid);
                CACHE.put(playerUuid, new CopyOnWriteArrayList<>(list));
            } finally {
                LOADING.remove(playerUuid);
            }
        });
    }

    public static void establishRelation(ServerLevel level, UUID playerUuid, String villageType, int posX, int posZ, String cityId, String cityName) {
        if (level == null || playerUuid == null) return;
        long establishedAt = System.currentTimeMillis();
        DiplomacyRelation relation = new DiplomacyRelation(villageType, posX, posZ, cityId, cityName, establishedAt);
        CACHE.computeIfAbsent(playerUuid, k -> new CopyOnWriteArrayList<>()).add(relation);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submit(db, "village_diplomacy",
                "diplomacy:" + playerUuid + ":" + posX + ":" + posZ, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO village_diplomacy(" +
                            "player_uuid, village_type, village_pos_x, village_pos_z, city_id, city_name, established_at) " +
                            "VALUES(?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, villageType != null ? villageType : "");
                ps.setInt(3, posX);
                ps.setInt(4, posZ);
                ps.setString(5, cityId != null ? cityId : "");
                ps.setString(6, cityName != null ? cityName : "");
                ps.setLong(7, establishedAt);
                ps.executeUpdate();
            }
        });
    }

    public static void removeRelation(ServerLevel level, UUID playerUuid, int posX, int posZ) {
        if (level == null || playerUuid == null) return;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "village_diplomacy",
                "diplomacy:" + playerUuid + ":" + posX + ":" + posZ, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM village_diplomacy WHERE player_uuid = ? AND village_pos_x = ? AND village_pos_z = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setInt(2, posX);
                ps.setInt(3, posZ);
                ps.executeUpdate();
            }
        });
        List<DiplomacyRelation> cached = CACHE.get(playerUuid);
        if (cached != null) {
            cached.removeIf(r -> r.posX() == posX && r.posZ() == posZ);
        }
    }

    public static List<DiplomacyRelation> loadRelationsForCity(ServerLevel level, String cityId) {
        if (level == null || cityId == null) return new ArrayList<>();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return new ArrayList<>();
        List<DiplomacyRelation> list = new ArrayList<>();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT village_type, village_pos_x, village_pos_z, city_id, city_name, established_at " +
                             "FROM village_diplomacy WHERE city_id = ?")) {
            ps.setString(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DiplomacyRelation(
                            rs.getString("village_type"),
                            rs.getInt("village_pos_x"),
                            rs.getInt("village_pos_z"),
                            rs.getString("city_id"),
                            rs.getString("city_name"),
                            rs.getLong("established_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load diplomacy relations for city", e);
        }
        return list;
    }

    public static void invalidateCache(UUID playerUuid) {
        if (playerUuid != null) CACHE.remove(playerUuid);
    }

    public static void clearAllCache() {
        CACHE.clear();
        LOADING.clear();
    }

    private static List<DiplomacyRelation> queryRelations(ServerLevel level, UUID playerUuid) {
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return new ArrayList<>();
        List<DiplomacyRelation> list = new ArrayList<>();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT village_type, village_pos_x, village_pos_z, city_id, city_name, established_at " +
                             "FROM village_diplomacy WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DiplomacyRelation(
                            rs.getString("village_type"),
                            rs.getInt("village_pos_x"),
                            rs.getInt("village_pos_z"),
                            rs.getString("city_id"),
                            rs.getString("city_name"),
                            rs.getLong("established_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load diplomacy relations", e);
        }
        return list;
    }
}