package com.xy2407.nsukaddition.common.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 采矿控制箱数据的 SQLite 持久化存储，写操作异步执行避免SQLITE_BUSY。 */
@SuppressWarnings("null")
public final class MiningBoxSqliteStorage {

    private MiningBoxSqliteStorage() {}

    private static SimuSqliteDatabase openDatabase(MinecraftServer server) {
        return NsukSqliteDatabase.get(server);
    }

    public static void clearServerCache(MinecraftServer server) {
        NsukSqliteDatabase.clearServerCache(server);
    }

    public static void saveBox(ServerLevel level, MiningBoxData data) {
        if (level == null || data == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                SimuSqliteDatabase db = openDatabase(server);
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
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to save mining box data", e);
            }
        });
    }

    public static void deleteBox(ServerLevel level, long boxPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                SimuSqliteDatabase db = openDatabase(server);
                try (Connection connection = db.openConnection();
                     PreparedStatement ps = connection.prepareStatement(
                             "DELETE FROM mining_boxes WHERE box_pos_long = ?")) {
                    ps.setLong(1, boxPosLong);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to delete mining box data", e);
            }
        });
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
