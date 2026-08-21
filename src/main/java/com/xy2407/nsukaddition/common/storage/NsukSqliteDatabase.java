package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import common.cn.kafei.simukraft.storage.core.SchemaMigrator;
import common.cn.kafei.simukraft.storage.core.SqlFunction;
import common.cn.kafei.simukraft.storage.core.SqlWrite;
import common.cn.kafei.simukraft.storage.core.SqliteConnectionPool;
import common.cn.kafei.simukraft.storage.core.StorageMetrics;
import common.cn.kafei.simukraft.storage.StorageWriteQueue;
import common.cn.kafei.simukraft.storage.core.TransactionRunner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * NSuk 模块独立 SQLite 数据库，与 SimuKraft 数据库完全分离，消除跨模块 WAL 写锁竞争。
 * 复用 SimuKraft 2.2.0 存储体系：{@link SqliteConnectionPool} 管连接、{@link TransactionRunner} 管事务、
 * {@link StorageWriteQueue} 管写入顺序、{@link SchemaMigrator} 管版本化建表/迁移。
 */
@SuppressWarnings("null")
public final class NsukSqliteDatabase implements Closeable {

    private static final String STORAGE_DIR = "nsuk_addition";
    private static final String DATABASE_FILE = "nsuk_addition.sqlite";
    private static final long SYNC_WRITE_TIMEOUT_MILLIS = 30_000L;
    private static final String[] NSUK_TABLES = {
            "colony", "colony_chunk", "colony_citizen",
            "breeding_boxes", "auto_restock",
            "foreign_trade_boxes", "free_market_listings", "restaurant_boxes",
            "village_diplomacy", "village_trade_quota", "village_city_type",
            "foreign_trade_caravans", "foreign_trade_caravan_members", "foreign_trade_shopping_list"
    };

    private static final ConcurrentMap<MinecraftServer, NsukSqliteDatabase> INSTANCES = new ConcurrentHashMap<>();
    private static final Set<MinecraftServer> SHUTDOWN = ConcurrentHashMap.newKeySet();

    private final Path databasePath;
    private final SqliteConnectionPool connections;
    private final TransactionRunner transactions;
    private final StorageMetrics metrics = new StorageMetrics();
    private final StorageWriteQueue writeQueue;
    private volatile boolean degraded;
    private volatile boolean closed;

    private NsukSqliteDatabase(Path databasePath, MinecraftServer server) {
        this.databasePath = databasePath;
        createStorageDirectory(databasePath);
        this.connections = SqliteConnectionPool.open(databasePath);
        this.transactions = new TransactionRunner(connections, this::markDegraded, metrics);
        try {
            new SchemaMigrator(NsukSqliteSchema::createBaseline, NsukMigrations.all()).migrate(connections);
        } catch (SQLException exception) {
            connections.close();
            throw new IllegalStateException("Failed to initialize NSuk SQLite schema", exception);
        }
        this.writeQueue = new StorageWriteQueue("nsuk-addition-db-write", transactions, metrics);
        migrateFromSimukraftDatabase(server);
    }

    public static NsukSqliteDatabase get(MinecraftServer server) {
        if (server == null || SHUTDOWN.contains(server)) {
            return null;
        }
        try {
            return INSTANCES.computeIfAbsent(server, key -> {
                Path dbPath = databasePath(key);
                return new NsukSqliteDatabase(dbPath, key);
            });
        } catch (RuntimeException exception) {
            NsukAddition.LOGGER.error("NSuk SQLite storage is unavailable. NSuk data will run on in-memory state only.", exception);
            return null;
        }
    }

    public static NsukSqliteDatabase getInstance() {
        var it = INSTANCES.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    public static Path databasePath(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return worldPath.resolve(STORAGE_DIR).resolve(DATABASE_FILE);
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) {
            return;
        }
        NsukSqliteDatabase removed = INSTANCES.remove(server);
        if (removed != null) {
            removed.closeCached();
        }
    }

    public static void closeFor(MinecraftServer server) {
        if (server == null) {
            return;
        }
        SHUTDOWN.add(server);
        NsukSqliteDatabase removed = INSTANCES.remove(server);
        if (removed != null) {
            removed.close();
        }
    }

    public static void forgetServer(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
            SHUTDOWN.remove(server);
        }
    }

    public Connection openConnection() throws SQLException {
        return connections.borrow();
    }

    public Connection borrowConnection() throws SQLException {
        return connections.borrow();
    }

    public <T> T callSync(SqlFunction<T> function) {
        if (isWriteBlocked()) {
            return null;
        }
        final Object[] result = {null};
        NsukWriteExecutor.submitSync(() -> result[0] = executeSync(function));
        return (T) result[0];
    }

    private Object executeSync(SqlFunction function) {
        try (Connection connection = borrowConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Object result = function.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception exception) {
            NsukAddition.LOGGER.error("NSuk: callSync failed", exception);
            return null;
        }
    }

    public void submitWrite(Object key, SqlWrite write) {
        if (isWriteBlocked()) {
            NsukAddition.LOGGER.warn("NSuk: async write for key {} skipped, storage is write-blocked.", key);
            return;
        }
        writeQueue.submit(key, write);
    }

    public void submitWrite(SqlWrite write) {
        if (isWriteBlocked()) {
            NsukAddition.LOGGER.warn("NSuk: async write skipped, storage is write-blocked.");
            return;
        }
        writeQueue.submitOnce(write);
    }

    public boolean isWriteBlocked() {
        return closed || degraded;
    }

    public void markDegraded(String context, Throwable cause) {
        if (!degraded) {
            degraded = true;
            NsukAddition.LOGGER.error("NSuk: SQLite storage entered DEGRADED mode ({}). Writes are disabled to protect existing data.", context, cause);
        }
    }

    public boolean isDegraded() {
        return degraded;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean drainWrites() {
        return writeQueue.drainAndReport();
    }

    public int pendingWrites() {
        return writeQueue.pendingCount();
    }

    public StorageMetrics metrics() {
        return metrics;
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        writeQueue.drainAndReport();
        writeQueue.close(5_000L);
        connections.close();
    }

    public void closeCached() {
        close();
    }

    private static void createStorageDirectory(Path databasePath) {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create NSuk SQLite directory", exception);
        }
    }

    private void migrateFromSimukraftDatabase(MinecraftServer server) {
        Path simuDbPath = SimuSqliteDatabase.databasePath(server);
        if (!Files.exists(simuDbPath)) {
            return;
        }

        Set<String> migratedTables = new HashSet<>();
        try (Connection conn = openConnection()) {
            String escapedPath = simuDbPath.toAbsolutePath().normalize().toString().replace("'", "''");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ATTACH DATABASE '" + escapedPath + "' AS old_db");
            }
            for (String table : NSUK_TABLES) {
                if (!tableExists(conn, "old_db", table)) {
                    continue;
                }
                List<String> commonCols = new ArrayList<>();
                List<String> oldCols = tableColumns(conn, "old_db", table);
                List<String> mainCols = tableColumns(conn, "main", table);
                for (String col : oldCols) {
                    if (mainCols.contains(col)) {
                        commonCols.add(col);
                    }
                }
                if (commonCols.isEmpty()) {
                    NsukAddition.LOGGER.warn("NSuk: skip migrating table {}: no common columns", table);
                    continue;
                }
                String cols = String.join(", ", commonCols);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("INSERT OR IGNORE INTO main." + table + "(" + cols + ") SELECT " + cols + " FROM old_db." + table);
                    migratedTables.add(table);
                } catch (SQLException e) {
                    NsukAddition.LOGGER.warn("NSuk: failed to migrate table {}: {}", table, e.getMessage());
                }
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DETACH DATABASE old_db");
            }
        } catch (SQLException e) {
            NsukAddition.LOGGER.warn("NSuk: data migration copy phase failed", e);
            return;
        }
        if (migratedTables.isEmpty()) {
            return;
        }

        try (Connection simuConn = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + simuDbPath.toAbsolutePath().normalize())) {
            try (Statement stmt = simuConn.createStatement()) {
                stmt.execute("PRAGMA busy_timeout=30000");
            }
            for (String table : migratedTables) {
                try (Statement stmt = simuConn.createStatement()) {
                    stmt.executeUpdate("DROP TABLE IF EXISTS " + table);
                }
            }
            NsukAddition.LOGGER.info("NSuk tables migrated and cleaned from simukraft.sqlite");
        } catch (SQLException e) {
            NsukAddition.LOGGER.warn("NSuk: old table cleanup failed (non-critical)", e);
        }
    }

    private static List<String> tableColumns(Connection conn, String schema, String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA " + schema + ".table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(rs.getString("name"));
                }
            }
        }
        return cols;
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
}