package com.xy2407.nsukaddition.common.autorestock;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** 自动补货方块位置的 SQLite 持久化存储，写操作异步执行避免SQLITE_BUSY。 */
@SuppressWarnings("null")
public final class AutoRestockSqliteStorage {

    private AutoRestockSqliteStorage() {}

    private static SimuSqliteDatabase openDatabase(MinecraftServer server) {
        return NsukSqliteDatabase.get(server);
    }

    public static void clearServerCache(MinecraftServer server) {
        NsukSqliteDatabase.clearServerCache(server);
    }

    public static void save(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                SimuSqliteDatabase db = openDatabase(server);
                if (db == null) return;
                try (Connection connection = db.openConnection();
                     PreparedStatement ps = connection.prepareStatement(
                             "INSERT INTO auto_restock(box_pos_long, updated_at) VALUES(?, ?) "
                                     + "ON CONFLICT(box_pos_long) DO UPDATE SET updated_at = excluded.updated_at")) {
                    ps.setLong(1, pos.asLong());
                    ps.setLong(2, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to save auto restock data", e);
            }
        });
    }

    public static void delete(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                SimuSqliteDatabase db = openDatabase(server);
                if (db == null) return;
                try (Connection connection = db.openConnection();
                     PreparedStatement ps = connection.prepareStatement(
                             "DELETE FROM auto_restock WHERE box_pos_long = ?")) {
                    ps.setLong(1, pos.asLong());
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to delete auto restock data", e);
            }
        });
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
