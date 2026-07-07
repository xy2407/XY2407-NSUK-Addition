package com.xy2407.nsukaddition.common.breeding;

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

/** 繁殖控制箱数据的 SQLite 持久化存储，支持单条存删与全量加载。 */
@SuppressWarnings("null")
public final class BreedingBoxSqliteStorage {

    private static final ConcurrentMap<String, SimuSqliteDatabase> DATABASES = new ConcurrentHashMap<>();

    private BreedingBoxSqliteStorage() {}

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
                    "CREATE TABLE IF NOT EXISTS breeding_boxes("
                            + "box_pos_long INTEGER PRIMARY KEY, "
                            + "building_id TEXT NOT NULL DEFAULT '', "
                            + "definition_id TEXT NOT NULL DEFAULT '', "
                            + "selected_recipe_id TEXT NOT NULL DEFAULT '', "
                            + "running INTEGER NOT NULL DEFAULT 0, "
                            + "status_key TEXT NOT NULL DEFAULT '', "
                            + "status_text TEXT NOT NULL DEFAULT '', "
                            + "progress_ticks INTEGER NOT NULL DEFAULT 0, "
                            + "cooldown_ticks INTEGER NOT NULL DEFAULT 0, "
                            + "work_state TEXT NOT NULL DEFAULT '', "
                            + "updated_at INTEGER NOT NULL DEFAULT 0"
                            + ")"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize breeding_boxes table in SQLite", exception);
        }
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) return;
        DATABASES.remove(SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString());
    }

    public static void saveBox(ServerLevel level, BreedingBoxData data) {
        if (level == null || data == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO breeding_boxes(box_pos_long, building_id, definition_id, selected_recipe_id, "
                                 + "running, status_key, status_text, progress_ticks, cooldown_ticks, work_state, updated_at) "
                                 + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                 + "ON CONFLICT(box_pos_long) DO UPDATE SET "
                                 + "building_id = excluded.building_id, definition_id = excluded.definition_id, "
                                 + "selected_recipe_id = excluded.selected_recipe_id, running = excluded.running, "
                                 + "status_key = excluded.status_key, status_text = excluded.status_text, "
                                 + "progress_ticks = excluded.progress_ticks, cooldown_ticks = excluded.cooldown_ticks, "
                                 + "work_state = excluded.work_state, updated_at = excluded.updated_at"
                 )) {
                ps.setLong(1, data.boxPos().asLong());
                ps.setString(2, data.buildingId());
                ps.setString(3, data.definitionId());
                ps.setString(4, data.selectedRecipeId());
                ps.setInt(5, data.running() ? 1 : 0);
                ps.setString(6, data.statusKey());
                ps.setString(7, data.statusText());
                ps.setInt(8, data.progressTicks());
                ps.setInt(9, data.cooldownTicks());
                ps.setString(10, data.workState());
                ps.setLong(11, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    public static void deleteBox(ServerLevel level, long boxPosLong) {
        if (level == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM breeding_boxes WHERE box_pos_long = ?")) {
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
                 PreparedStatement ps = connection.prepareStatement("SELECT * FROM breeding_boxes ORDER BY box_pos_long");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CompoundTag box = new CompoundTag();
                    box.putLong("BoxPos", rs.getLong("box_pos_long"));
                    box.putString("BuildingId", rs.getString("building_id"));
                    box.putString("DefinitionId", rs.getString("definition_id"));
                    box.putString("SelectedRecipeId", rs.getString("selected_recipe_id"));
                    box.putBoolean("Running", rs.getInt("running") != 0);
                    box.putString("StatusKey", rs.getString("status_key"));
                    box.putString("StatusText", rs.getString("status_text"));
                    box.putInt("ProgressTicks", rs.getInt("progress_ticks"));
                    box.putInt("CooldownTicks", rs.getInt("cooldown_ticks"));
                    box.putString("WorkState", rs.getString("work_state"));
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
