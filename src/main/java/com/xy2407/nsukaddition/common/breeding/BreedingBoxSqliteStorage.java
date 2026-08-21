package com.xy2407.nsukaddition.common.breeding;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 繁殖控制箱数据的 SQLite 持久化存储，支持单条存删与全量加载。写入走 WriteBatchBuffer 批量缓冲，删除立即落库。 */
@SuppressWarnings("null")
public final class BreedingBoxSqliteStorage {

    private static final String TABLE = "breeding_boxes";

    private BreedingBoxSqliteStorage() {}

    private static NsukSqliteDatabase openDatabase(MinecraftServer server) {
        return NsukSqliteDatabase.get(server);
    }

    public static void clearServerCache(MinecraftServer server) {
        NsukSqliteDatabase.clearServerCache(server);
    }

    public static void saveBox(ServerLevel level, BreedingBoxData data, Runnable onComplete) {
        if (level == null || data == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        CompoundTag snapshot = data.toTag();
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        long boxPosLong = snapshot.getLong("BoxPos");
        WriteBatchBuffer.submit(db, TABLE, "nsuk_breeding_box:" + boxPosLong, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
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
                ps.setLong(1, boxPosLong);
                ps.setString(2, snapshot.getString("BuildingId"));
                ps.setString(3, snapshot.getString("DefinitionId"));
                ps.setString(4, snapshot.getString("SelectedRecipeId"));
                ps.setInt(5, snapshot.getBoolean("Running") ? 1 : 0);
                ps.setString(6, snapshot.getString("StatusKey"));
                ps.setString(7, snapshot.getString("StatusText"));
                ps.setInt(8, snapshot.getInt("ProgressTicks"));
                ps.setInt(9, snapshot.getInt("CooldownTicks"));
                ps.setString(10, snapshot.getString("WorkState"));
                ps.setLong(11, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
        if (onComplete != null) onComplete.run();
    }

    public static void deleteBox(ServerLevel level, long boxPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, TABLE, "nsuk_breeding_box:" + boxPosLong, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM breeding_boxes WHERE box_pos_long = ?")) {
                ps.setLong(1, boxPosLong);
                ps.executeUpdate();
            }
        });
    }

    public static CompoundTag loadAll(ServerLevel level) {
        if (level == null) return null;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to load breeding box data", e);
        }
    }
}
