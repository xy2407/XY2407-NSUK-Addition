package com.xy2407.nsukaddition.common.autorestock;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** 自动补货方块位置的 SQLite 持久化存储，写入走 WriteBatchBuffer 20 tick 批量缓冲，删除立即落库。 */
@SuppressWarnings("null")
public final class AutoRestockSqliteStorage {

    private static final String TABLE = "auto_restock";

    private AutoRestockSqliteStorage() {}

    private static NsukSqliteDatabase openDatabase(MinecraftServer server) {
        return NsukSqliteDatabase.get(server);
    }

    public static void clearServerCache(MinecraftServer server) {
        NsukSqliteDatabase.clearServerCache(server);
    }

    public static void save(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        long posLong = pos.asLong();
        WriteBatchBuffer.submit(db, TABLE, TABLE + ":" + posLong, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO auto_restock(box_pos_long, updated_at) VALUES(?, ?) "
                            + "ON CONFLICT(box_pos_long) DO UPDATE SET updated_at = excluded.updated_at")) {
                ps.setLong(1, posLong);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public static void delete(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = openDatabase(server);
        if (db == null) return;
        long posLong = pos.asLong();
        WriteBatchBuffer.submitPriority(db, TABLE, TABLE + ":" + posLong, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM auto_restock WHERE box_pos_long = ?")) {
                ps.setLong(1, posLong);
                ps.executeUpdate();
            }
        });
    }

    public static Set<BlockPos> loadAll(ServerLevel level) {
        Set<BlockPos> positions = new HashSet<>();
        if (level == null) return positions;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
            if (db == null) return positions;
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT box_pos_long FROM auto_restock");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    positions.add(BlockPos.of(rs.getLong("box_pos_long")));
                }
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to load auto restock positions from database", e);
        }
        return positions;
    }
}