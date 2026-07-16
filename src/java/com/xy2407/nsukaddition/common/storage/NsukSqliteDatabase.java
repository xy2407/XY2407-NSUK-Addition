package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** NSuk模块独立SQLite数据库，与SimuKraft数据库完全分离，消除跨模组WAL写锁竞争。 */
@SuppressWarnings("null")
public final class NsukSqliteDatabase implements AutoCloseable {

    private static final String STORAGE_DIR = "nsuk_addition";
    private static final String DATABASE_FILE = "nsuk_addition.sqlite";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final AtomicBoolean DRIVER_LOADED = new AtomicBoolean();
    private static final String[] NSUK_TABLES = {
            "colony", "colony_chunk", "colony_citizen",
            "breeding_boxes", "mining_boxes", "auto_restock", "citizen_equipment",
            "foreign_trade_boxes", "free_market_listings"
    };

    private static final ConcurrentMap<String, NsukSqliteDatabase> DATABASES = new ConcurrentHashMap<>();

    private final Path databasePath;
    private final String jdbcUrl;

    private NsukSqliteDatabase(Path databasePath, MinecraftServer server) {
        loadDriver();
        this.databasePath = databasePath;
        this.jdbcUrl = JDBC_PREFIX + databasePath.toAbsolutePath().normalize();
        initAllTables();
        migrateFromSimukraftDatabase(server);
    }

    public static NsukSqliteDatabase get(MinecraftServer server) {
        if (server == null) return null;
        Path dbPath = databasePath(server);
        String key = dbPath.toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent(key, ignored -> new NsukSqliteDatabase(dbPath, server));
    }

    public static NsukSqliteDatabase getInstance() {
        var it = DATABASES.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    public static Path databasePath(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return worldPath.resolve(STORAGE_DIR).resolve(DATABASE_FILE);
    }

    public Connection openConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = c.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=30000");
            stmt.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            try { c.close(); } catch (SQLException ignored) {}
            throw e;
        }
        return c;
    }

    private void initAllTables() {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create NSuk database directory", e);
        }
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS colony("
                    + "colony_id TEXT PRIMARY KEY, parent_city TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', "
                    + "core_pos_long INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS colony_chunk("
                    + "colony_id TEXT NOT NULL, dimension TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, "
                    + "PRIMARY KEY (colony_id, dimension, chunk_x, chunk_z))");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS colony_citizen("
                    + "citizen_uuid TEXT PRIMARY KEY, colony_id TEXT NOT NULL, assigned_at INTEGER NOT NULL DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS breeding_boxes("
                    + "box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', "
                    + "selected_recipe_id TEXT NOT NULL DEFAULT '', running INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', "
                    + "status_text TEXT NOT NULL DEFAULT '', progress_ticks INTEGER NOT NULL DEFAULT 0, cooldown_ticks INTEGER NOT NULL DEFAULT 0, "
                    + "work_state TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mining_boxes("
                    + "box_pos_long INTEGER PRIMARY KEY, current_y_level INTEGER NOT NULL, work_ticks INTEGER NOT NULL DEFAULT 0, "
                    + "running INTEGER NOT NULL DEFAULT 0, auto_restock INTEGER NOT NULL DEFAULT 0, "
                    + "status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', "
                    + "updated_at INTEGER NOT NULL DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS auto_restock("
                    + "box_pos_long INTEGER PRIMARY KEY, updated_at INTEGER NOT NULL DEFAULT 0)");
            try { stmt.executeUpdate("ALTER TABLE mining_boxes ADD COLUMN auto_restock INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS citizen_equipment("
                    + "uuid TEXT PRIMARY KEY, head BLOB, chest BLOB, legs BLOB, feet BLOB)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS foreign_trade_boxes("
                    + "box_pos_long INTEGER PRIMARY KEY, running INTEGER NOT NULL DEFAULT 0, "
                    + "status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', "
                    + "selected_trade_id TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            FreeMarketRepository.ensureTable(this);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize NSuk SQLite tables", e);
        }
    }

    private void migrateFromSimukraftDatabase(MinecraftServer server) {
        Path simuDbPath = SimuSqliteDatabase.databasePath(server);
        if (!Files.exists(simuDbPath)) return;

        boolean hasAnyOldTable = false;
        try (Connection conn = openConnection()) {
            String escapedPath = simuDbPath.toAbsolutePath().normalize().toString().replace("'", "''");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ATTACH DATABASE '" + escapedPath + "' AS old_db");
            }
            for (String table : NSUK_TABLES) {
                if (tableExists(conn, "old_db", table)) {
                    hasAnyOldTable = true;
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("INSERT OR IGNORE INTO main." + table + " SELECT * FROM old_db." + table);
                    } catch (SQLException e) {
                        NsukAddition.LOGGER.warn("Failed to migrate table {}: {}", table, e.getMessage());
                    }
                }
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DETACH DATABASE old_db");
            }
        } catch (SQLException e) {
            NsukAddition.LOGGER.warn("NSuk data migration copy phase failed", e);
            return;
        }
        if (!hasAnyOldTable) return;

        try (Connection simuConn = DriverManager.getConnection(
                JDBC_PREFIX + simuDbPath.toAbsolutePath().normalize())) {
            try (Statement stmt = simuConn.createStatement()) {
                stmt.execute("PRAGMA busy_timeout=30000");
            }
            for (String table : NSUK_TABLES) {
                try (Statement stmt = simuConn.createStatement()) {
                    stmt.executeUpdate("DROP TABLE IF EXISTS " + table);
                }
            }
            NsukAddition.LOGGER.info("NSuk tables migrated and cleaned from simukraft.sqlite");
        } catch (SQLException e) {
            NsukAddition.LOGGER.warn("NSuk old table cleanup failed (non-critical)", e);
        }
    }

    private static boolean tableExists(Connection conn, String schema, String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM " + schema + ".sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) return;
        String key = databasePath(server).toAbsolutePath().normalize().toString();
        DATABASES.remove(key);
    }

    @Override
    public void close() {}

    private static void loadDriver() {
        if (DRIVER_LOADED.get()) return;
        synchronized (DRIVER_LOADED) {
            if (DRIVER_LOADED.get()) return;
            try {
                Class.forName("org.sqlite.JDBC");
                DRIVER_LOADED.set(true);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("SQLite JDBC driver not available", e);
            }
        }
    }
}
