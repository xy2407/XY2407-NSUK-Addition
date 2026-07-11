package com.xy2407.nsukaddition.common.storage;

import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.server.MinecraftServer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** NSuk模块共享SQLite数据库实例，写操作通过 NsukWriteExecutor 异步串行化，避免SQLITE_BUSY冲突。 */
@SuppressWarnings("null")
public final class NsukSqliteDatabase {

    private static final ConcurrentMap<String, SimuSqliteDatabase> DATABASES = new ConcurrentHashMap<>();
    private static volatile boolean tablesInitialized = false;

    private NsukSqliteDatabase() {}

    public static SimuSqliteDatabase get(MinecraftServer server) {
        if (server == null) return null;
        String key = SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent(key, ignored -> {
            SimuSqliteDatabase db = SimuSqliteDatabase.open(server);
            initAllTables(db);
            return db;
        });
    }

    private static void initAllTables(SimuSqliteDatabase db) {
        if (tablesInitialized) return;
        synchronized (NsukSqliteDatabase.class) {
            if (tablesInitialized) return;
            tablesInitialized = true;
            try (Connection conn = db.openConnection();
                 Statement stmt = conn.createStatement()) {
                // 附属地表
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS colony("
                        + "colony_id TEXT PRIMARY KEY, parent_city TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', "
                        + "core_pos_long INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL DEFAULT 0)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS colony_chunk("
                        + "colony_id TEXT NOT NULL, dimension TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, "
                        + "PRIMARY KEY (colony_id, dimension, chunk_x, chunk_z))");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS colony_citizen("
                        + "citizen_uuid TEXT PRIMARY KEY, colony_id TEXT NOT NULL, assigned_at INTEGER NOT NULL DEFAULT 0)");
                // 繁殖箱表
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS breeding_boxes("
                        + "box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', "
                        + "selected_recipe_id TEXT NOT NULL DEFAULT '', running INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', "
                        + "status_text TEXT NOT NULL DEFAULT '', progress_ticks INTEGER NOT NULL DEFAULT 0, cooldown_ticks INTEGER NOT NULL DEFAULT 0, "
                        + "work_state TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
                // 采矿箱表
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mining_boxes("
                        + "box_pos_long INTEGER PRIMARY KEY, current_y_level INTEGER NOT NULL, work_ticks INTEGER NOT NULL DEFAULT 0, "
                        + "running INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', "
                        + "updated_at INTEGER NOT NULL DEFAULT 0)");
                // 自动补货表
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS auto_restock("
                        + "box_pos_long INTEGER PRIMARY KEY, updated_at INTEGER NOT NULL DEFAULT 0)");
                // 市民装备表
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS citizen_equipment("
                        + "uuid TEXT PRIMARY KEY, head BLOB, chest BLOB, legs BLOB, feet BLOB)");
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to initialize NSuk SQLite tables", e);
            }
        }
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) return;
        String key = SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString();
        DATABASES.remove(key);
        tablesInitialized = false;
    }
}
