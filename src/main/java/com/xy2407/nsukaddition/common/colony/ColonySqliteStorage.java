package com.xy2407.nsukaddition.common.colony;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 附属领地数据的 SQLite 持久化存储，管理附属地主表、区块表和居民表。 */
@SuppressWarnings("null")
public final class ColonySqliteStorage {

    private static final ThreadLocal<ServerLevel> LEVEL_CACHE = new ThreadLocal<>();

    private static final ConcurrentMap<String, List<UUID>> COLONY_ID_CACHE = new ConcurrentHashMap<>();

    private ColonySqliteStorage() {}

    public static void setCachedLevel(ServerLevel level) { LEVEL_CACHE.set(level); }

    public static ServerLevel getCachedLevel() { return LEVEL_CACHE.get(); }

    public static void clearCachedLevel() { LEVEL_CACHE.remove(); }

    public static void clearCache() {
        COLONY_ID_CACHE.clear();
    }

    private static NsukSqliteDatabase openDatabase(MinecraftServer server) {
        return NsukSqliteDatabase.get(server);
    }

    public static void saveColony(ServerLevel level, ColonyData data) {
        if (level == null || data == null) return;
        COLONY_ID_CACHE.computeIfAbsent(data.dimensionId(), k -> new CopyOnWriteArrayList<>());
        if (!COLONY_ID_CACHE.get(data.dimensionId()).contains(data.colonyId())) {
            COLONY_ID_CACHE.get(data.dimensionId()).add(data.colonyId());
        }
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        WriteBatchBuffer.submit(db, "colony", "colony:" + data.colonyId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO colony(colony_id, parent_city, name, core_pos_long, dimension_id, created_at) "
                            + "VALUES(?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(colony_id) DO UPDATE SET "
                            + "parent_city = excluded.parent_city, name = excluded.name, "
                            + "core_pos_long = excluded.core_pos_long, dimension_id = excluded.dimension_id, "
                            + "created_at = excluded.created_at"
            )) {
                ps.setString(1, data.colonyId().toString());
                ps.setString(2, data.parentCityId().toString());
                ps.setString(3, data.name());
                ps.setLong(4, data.corePos().asLong());
                ps.setString(5, data.dimensionId());
                ps.setLong(6, data.createdAt());
                ps.executeUpdate();
            }
        });
    }

    public static void deleteColony(ServerLevel level, UUID colonyId) {
        if (level == null || colonyId == null) return;
        COLONY_ID_CACHE.values().forEach(list -> list.remove(colonyId));
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "colony", "colony:" + colonyId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM colony_citizen WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM colony_chunk WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM colony WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                ps.executeUpdate();
            }
        });
    }

    public static List<ColonyData> loadColoniesByParentCity(ServerLevel level, UUID parentCityId) {
        List<ColonyData> result = new ArrayList<>();
        if (level == null || parentCityId == null) return result;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM colony WHERE parent_city = ? ORDER BY created_at")) {
                ps.setString(1, parentCityId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ColonyData(
                                UUID.fromString(rs.getString("colony_id")),
                                UUID.fromString(rs.getString("parent_city")),
                                rs.getString("name"),
                                BlockPos.of(rs.getLong("core_pos_long")),
                                rs.getString("dimension_id"),
                                rs.getLong("created_at")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load colonies by parent city", e);
        }
        return result;
    }

    public static List<UUID> loadAllColonyIds(ServerLevel level, String dimensionId) {
        if (level == null || dimensionId == null) return new ArrayList<>();
        List<UUID> cached = COLONY_ID_CACHE.get(dimensionId);
        if (cached != null) return new ArrayList<>(cached);
        List<UUID> result = new ArrayList<>();
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT colony_id FROM colony WHERE dimension_id = ?")) {
                ps.setString(1, dimensionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(UUID.fromString(rs.getString("colony_id")));
                    }
                }
            }
            COLONY_ID_CACHE.put(dimensionId, new CopyOnWriteArrayList<>(result));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load all colony ids", e);
        }
        return result;
    }

    public static ColonyData loadColonyByCorePos(ServerLevel level, String dimensionId, long corePosLong) {
        if (level == null) return null;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM colony WHERE dimension_id = ? AND core_pos_long = ?")) {
                ps.setString(1, dimensionId);
                ps.setLong(2, corePosLong);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new ColonyData(
                                UUID.fromString(rs.getString("colony_id")),
                                UUID.fromString(rs.getString("parent_city")),
                                rs.getString("name"),
                                BlockPos.of(rs.getLong("core_pos_long")),
                                rs.getString("dimension_id"),
                                rs.getLong("created_at")
                        );
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load colony by core pos", e);
        }
        return null;
    }

    public static ColonyData loadColonyById(ServerLevel level, UUID colonyId) {
        if (level == null || colonyId == null) return null;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM colony WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new ColonyData(
                                UUID.fromString(rs.getString("colony_id")),
                                UUID.fromString(rs.getString("parent_city")),
                                rs.getString("name"),
                                BlockPos.of(rs.getLong("core_pos_long")),
                                rs.getString("dimension_id"),
                                rs.getLong("created_at")
                        );
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load colony by id", e);
        }
        return null;
    }

    public static void addChunk(ServerLevel level, UUID colonyId, String dimension, int chunkX, int chunkZ) {
        if (level == null || colonyId == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        db.callSync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO colony_chunk(colony_id, dimension, chunk_x, chunk_z) VALUES(?, ?, ?, ?)")) {
                ps.setString(1, colonyId.toString());
                ps.setString(2, dimension);
                ps.setInt(3, chunkX);
                ps.setInt(4, chunkZ);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public static void removeChunk(ServerLevel level, UUID colonyId, String dimension, int chunkX, int chunkZ) {
        if (level == null || colonyId == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        db.callSync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM colony_chunk WHERE colony_id = ? AND dimension = ? AND chunk_x = ? AND chunk_z = ?")) {
                ps.setString(1, colonyId.toString());
                ps.setString(2, dimension);
                ps.setInt(3, chunkX);
                ps.setInt(4, chunkZ);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public static int countChunksByParentCity(ServerLevel level, UUID parentCityId) {
        if (level == null || parentCityId == null) return 0;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM colony_chunk cc JOIN colony c ON cc.colony_id = c.colony_id WHERE c.parent_city = ?")) {
                ps.setString(1, parentCityId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to count colony chunks", e);
        }
    }

    public static int countChunksByColony(ServerLevel level, UUID colonyId) {
        if (level == null || colonyId == null) return 0;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM colony_chunk WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to count colony chunks by colony", e);
        }
    }

    public static List<ChunkEntry> loadChunksByColony(ServerLevel level, UUID colonyId) {
        List<ChunkEntry> result = new ArrayList<>();
        if (level == null || colonyId == null) return result;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT dimension, chunk_x, chunk_z FROM colony_chunk WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ChunkEntry(rs.getString("dimension"), rs.getInt("chunk_x"), rs.getInt("chunk_z")));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load chunks by colony", e);
        }
        return result;
    }

    public record ChunkEntry(String dimension, int x, int z) {}

    public static boolean hasChunk(ServerLevel level, UUID colonyId, String dimension, int chunkX, int chunkZ) {
        if (level == null || colonyId == null) return false;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM colony_chunk WHERE colony_id = ? AND dimension = ? AND chunk_x = ? AND chunk_z = ?")) {
                ps.setString(1, colonyId.toString());
                ps.setString(2, dimension);
                ps.setInt(3, chunkX);
                ps.setInt(4, chunkZ);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check colony chunk", e);
        }
    }

    public static void assignCitizen(ServerLevel level, UUID citizenUuid, UUID colonyId) {
        if (level == null || citizenUuid == null || colonyId == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        WriteBatchBuffer.submit(db, "colony_citizen", "colony_citizen:" + citizenUuid, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO colony_citizen(citizen_uuid, colony_id, assigned_at) VALUES(?, ?, ?) "
                            + "ON CONFLICT(citizen_uuid) DO UPDATE SET colony_id = excluded.colony_id, assigned_at = excluded.assigned_at")) {
                ps.setString(1, citizenUuid.toString());
                ps.setString(2, colonyId.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public static void removeCitizen(ServerLevel level, UUID citizenUuid) {
        if (level == null || citizenUuid == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "colony_citizen", "colony_citizen:" + citizenUuid, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM colony_citizen WHERE citizen_uuid = ?")) {
                ps.setString(1, citizenUuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public static UUID getColonyForCitizen(ServerLevel level, UUID citizenUuid) {
        if (level == null || citizenUuid == null) return null;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT colony_id FROM colony_citizen WHERE citizen_uuid = ?")) {
                ps.setString(1, citizenUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString("colony_id")) : null;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get colony for citizen", e);
        }
    }

    public static UUID getColonyForCitizen(UUID citizenUuid) {
        if (citizenUuid == null) return null;
        try {
            NsukSqliteDatabase db = NsukSqliteDatabase.getInstance();
            if (db == null) return null;
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT colony_id FROM colony_citizen WHERE citizen_uuid = ?")) {
                ps.setString(1, citizenUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString("colony_id")) : null;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get colony for citizen", e);
        }
    }

    public static List<UUID> loadCitizensByColony(ServerLevel level, UUID colonyId) {
        List<UUID> result = new ArrayList<>();
        if (level == null || colonyId == null) return result;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT citizen_uuid FROM colony_citizen WHERE colony_id = ?")) {
                ps.setString(1, colonyId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(UUID.fromString(rs.getString("citizen_uuid")));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load colony citizens", e);
        }
        return result;
    }

    public static UUID getColonyByCorePos(ServerLevel level, BlockPos corePos) {
        if (level == null || corePos == null) return null;
        long posLong = corePos.asLong();
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT colony_id FROM colony WHERE core_pos_long = ?")) {
                ps.setLong(1, posLong);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString("colony_id")) : null;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get colony by core pos", e);
        }
    }
}