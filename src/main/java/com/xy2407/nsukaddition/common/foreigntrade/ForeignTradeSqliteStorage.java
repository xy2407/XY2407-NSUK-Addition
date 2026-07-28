package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 外贸控制箱SQLite持久化，负责单个外贸箱的状态读写。 */
@SuppressWarnings("null")
public final class ForeignTradeSqliteStorage {

    private ForeignTradeSqliteStorage() {}

    public static ForeignTradeBoxData load(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return null;
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT running, status_key, status_text, selected_trade_id FROM foreign_trade_boxes WHERE box_pos_long = ?")) {
            ps.setLong(1, pos.asLong());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ForeignTradeBoxData(
                            rs.getBoolean("running"),
                            rs.getString("status_key"),
                            rs.getString("status_text"),
                            rs.getString("selected_trade_id")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load foreign trade box data", e);
        }
        return null;
    }

    public static void save(ServerLevel level, BlockPos pos, boolean running, String statusKey, String statusText, String selectedTradeId) {
        if (level == null || pos == null) return;
        NsukWriteExecutor.submit(() -> {
            NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
            if (db == null) return;
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO foreign_trade_boxes(box_pos_long, running, status_key, status_text, selected_trade_id, updated_at) " +
                                 "VALUES(?, ?, ?, ?, ?, strftime('%s','now'))")) {
                ps.setLong(1, pos.asLong());
                ps.setBoolean(2, running);
                ps.setString(3, statusKey != null ? statusKey : "");
                ps.setString(4, statusText != null ? statusText : "");
                ps.setString(5, selectedTradeId != null ? selectedTradeId : "");
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save foreign trade box data", e);
            }
        });
    }

    public static void delete(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        NsukWriteExecutor.submit(() -> {
            NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
            if (db == null) return;
            try (Connection conn = db.openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM foreign_trade_boxes WHERE box_pos_long = ?")) {
                ps.setLong(1, pos.asLong());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete foreign trade box data", e);
            }
        });
    }

    public record ForeignTradeBoxData(boolean running, String statusKey, String statusText, String selectedTradeId) {}
}
