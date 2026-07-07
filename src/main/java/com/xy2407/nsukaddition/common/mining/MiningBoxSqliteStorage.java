package com.xy2407.nsukaddition.common.mining;

import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 采矿控制箱数据的 SQLite 持久化存储，支持单条存删与全量加载。 */
@SuppressWarnings("null")
public final class MiningBoxSqliteStorage {

    private static final ConcurrentMap<String, SimuSqliteDatabase> DATABASES = new ConcurrentHashMap<>();

    private MiningBoxSqliteStorage() {}

    private static SimuSqliteDatabase openDatabase(MinecraftServer server) {
        String key = SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent(key, ignored -> {
            SimuSqliteDatabase db = SimuSqliteDatabase.open(server);
            initTable(db);
            return db;
        });
    }

    private static void initTable(SimuSqliteDatabase database) {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mining_boxes("
                            + "box_pos_long INTEGER PRIMARY KEY, "
                            + "current_y_level INTEGER NOT NULL, "
                            + "work_ticks INTEGER NOT NULL DEFAULT 0, "
                            + "running INTEGER NOT NULL DEFAULT 0, "
                            + "status_key TEXT NOT NULL DEFAULT '', "
                            + "status_text TEXT NOT NULL DEFAULT '', "
                            + "updated_at INTEGER NOT NULL DEFAULT 0"
                            + ")"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize mining_boxes table in SQLite", exception);
        }
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) return;
        DATABASES.remove(SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString());
    }

    public static void saveBox(ServerLevel level, MiningBoxData data) {
        if (level == null || data == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO mining_boxes(box_pos_long, current_y_level, work_ticks, running, status_key, status_text, updated_at) "
                                 + "VALUES(?, ?, ?, ?, ?, ?, ?) "
                                 + "ON CONFLICT(box_pos_long) DO UPDATE SET current_y_level = excluded.current_y_level, "
                                 + "work_ticks = excluded.work_ticks, running = excluded.running, "
                                 + "status_key = excluded.status_key, status_text = excluded.status_text, "
                                 + "updated_at = excluded.updated_at"
                 )) {
                ps.setLong(1, data.boxPos().asLong());
                ps.setInt(2, data.currentYLevel());
                ps.setInt(3, data.workTicks());
                ps.setInt(4, data.running() ? 1 : 0);
                ps.setString(5, data.statusKey());
                ps.setString(6, data.statusText());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (Exception exception) {

        }
    }

    public static void deleteBox(ServerLevel level, long boxPosLong) {
        if (level == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM mining_boxes WHERE box_pos_long = ?")) {
                ps.setLong(1, boxPosLong);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    public static CompoundTag loadAll(ServerLevel level) {
        if (level == null) return null;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT * FROM mining_boxes ORDER BY box_pos_long");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CompoundTag box = new CompoundTag();
                    box.putLong("BoxPos", rs.getLong("box_pos_long"));
                    box.putInt("CurrentYLevel", rs.getInt("current_y_level"));
                    box.putInt("WorkTicks", rs.getInt("work_ticks"));
                    box.putBoolean("Running", rs.getInt("running") != 0);
                    box.putString("StatusKey", rs.getString("status_key"));
                    box.putString("StatusText", rs.getString("status_text"));
                    list.add(box);
                }
            }
            tag.put("Boxes", list);
            return list.isEmpty() ? null : tag;
        } catch (Exception exception) {
            return null;
        }
    }
}
