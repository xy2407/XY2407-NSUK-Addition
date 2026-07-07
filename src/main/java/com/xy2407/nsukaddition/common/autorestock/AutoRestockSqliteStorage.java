package com.xy2407.nsukaddition.common.autorestock;

import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 自动补货方块位置的 SQLite 持久化存储，负责增删查及缓存管理。 */
@SuppressWarnings("null")
public final class AutoRestockSqliteStorage {

    private static final ConcurrentMap<String, SimuSqliteDatabase> DATABASES = new ConcurrentHashMap<>();

    private AutoRestockSqliteStorage() {}

    private static SimuSqliteDatabase openDatabase(MinecraftServer server) {
        if (server == null) return null;
        String key = SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent(key, ignored -> {
            SimuSqliteDatabase db = SimuSqliteDatabase.open(server);
            initTable(db);
            return db;
        });
    }

    private static void initTable(SimuSqliteDatabase database) {
        if (database == null) return;
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS auto_restock("
                            + "box_pos_long INTEGER PRIMARY KEY, "
                            + "updated_at INTEGER NOT NULL DEFAULT 0"
                            + ")"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize auto_restock table in SQLite", exception);
        }
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) return;
        DATABASES.remove(SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString());
    }

    public static void save(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            if (db == null) return;
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO auto_restock(box_pos_long, updated_at) VALUES(?, ?) "
                                 + "ON CONFLICT(box_pos_long) DO UPDATE SET updated_at = excluded.updated_at")) {
                ps.setLong(1, pos.asLong());
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {

        }
    }

    public static void delete(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            if (db == null) return;
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM auto_restock WHERE box_pos_long = ?")) {
                ps.setLong(1, pos.asLong());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    public static Set<BlockPos> loadAll(ServerLevel level) {
        Set<BlockPos> positions = new HashSet<>();
        if (level == null) return positions;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            if (db == null) return positions;
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT box_pos_long FROM auto_restock");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    positions.add(BlockPos.of(rs.getLong("box_pos_long")));
                }
            }
        } catch (Exception ignored) {}
        return positions;
    }
}
