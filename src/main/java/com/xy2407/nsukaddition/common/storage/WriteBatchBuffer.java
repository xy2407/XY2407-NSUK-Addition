package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.storage.core.SqlWrite;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量写入缓冲区：双缓冲 + 主键去重 + 20 tick 周期批量提交。
 * 主线程所有写入请求先进 active 缓冲区按 table+key 去重，
 * 20 tick 后原子切换到快照，提交给 NsukWriteExecutor 单线程执行。
 * flush 时按 table 合并事务：同表多个 key 共享一个连接、一次 commit，减少 SQLite commit/fsync 次数。
 * 写线程通过 ThreadLocal 标志绕过 Mixin 拦截直接执行原始 save。
 */
public final class WriteBatchBuffer {

    public static final int FLUSH_INTERVAL_TICKS = 20;
    private static final int MAX_BUFFER_SIZE = 500;
    @SuppressWarnings("null")
    private static volatile ConcurrentHashMap<String, ConcurrentHashMap<String, WriteEntry>> active = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> WRITER_FLAG = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> IMMEDIATE_FLAG = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger bufferSize = new AtomicInteger(0);
    private static final AtomicBoolean flushingNow = new AtomicBoolean(false);

    private WriteBatchBuffer() {
    }

    private record WriteEntry(ConnectionSupplier supplier, SqlWrite write) {
    }

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    public static boolean isWriterThread() {
        return WRITER_FLAG.get();
    }

    public static void enterWriter() {
        WRITER_FLAG.set(true);
    }

    public static void exitWriter() {
        WRITER_FLAG.set(false);
    }

    public static boolean isImmediate() {
        return IMMEDIATE_FLAG.get();
    }

    public static void setImmediate(boolean immediate) {
        IMMEDIATE_FLAG.set(immediate);
    }

    public static void removeBucket(String table) {
        if (table == null) return;
        ConcurrentHashMap<String, WriteEntry> bucket = active.get(table);
        if (bucket == null) return;
        int removed = 0;
        for (String key : bucket.keySet()) {
            if (bucket.remove(key) != null) {
                removed++;
            }
        }
        bufferSize.addAndGet(-removed);
    }

    public static void removeKey(String table, String key) {
        if (table == null || key == null) return;
        ConcurrentHashMap<String, WriteEntry> bucket = active.get(table);
        if (bucket != null && bucket.remove(key) != null) {
            bufferSize.decrementAndGet();
        }
    }

    public static void submit(String table, String key, ConnectionSupplier supplier, SqlWrite write) {
        if (table == null || key == null || supplier == null || write == null) return;
        if (isWriterThread()) {
            executeOwnConnection(supplier, write);
            return;
        }
        ConcurrentHashMap<String, WriteEntry> bucket = active.computeIfAbsent(table, k -> new ConcurrentHashMap<>());
        if (bucket.put(key, new WriteEntry(supplier, write)) == null) {
            int size = bufferSize.incrementAndGet();
            if (size >= MAX_BUFFER_SIZE) {
                triggerFlush();
            }
        }
    }

    public static void triggerFlush() {
        if (!flushingNow.compareAndSet(false, true)) return;
        if (bufferSize.get() == 0) {
            flushingNow.set(false);
            return;
        }
        ConcurrentHashMap<String, ConcurrentHashMap<String, WriteEntry>> snapshot = active;
        active = new ConcurrentHashMap<>();
        bufferSize.set(0);
        NsukWriteExecutor.submit(() -> executeFlush(snapshot));
    }

    public static void flushEntry(String table, String key) {
        if (table == null || key == null) return;
        ConcurrentHashMap<String, WriteEntry> bucket = active.get(table);
        if (bucket == null) return;
        WriteEntry entry = bucket.remove(key);
        if (entry == null) return;
        bufferSize.decrementAndGet();
        NsukWriteExecutor.submit(() -> {
            WRITER_FLAG.set(true);
            try {
                executeOwnConnection(entry.supplier(), entry.write());
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Priority flush failed for table={} key={}", table, key, e);
            } finally {
                WRITER_FLAG.set(false);
            }
        });
    }

    public static void submitPriority(String table, String key, Runnable writer) {
        if (table == null || key == null || writer == null) return;
        ConcurrentHashMap<String, WriteEntry> bucket = active.get(table);
        if (bucket != null && bucket.remove(key) != null) {
            bufferSize.decrementAndGet();
        }
        NsukWriteExecutor.submit(() -> {
            WRITER_FLAG.set(true);
            try {
                writer.run();
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Priority submit failed for table={} key={}", table, key, e);
            } finally {
                WRITER_FLAG.set(false);
            }
        });
    }

    public static void submit(NsukSqliteDatabase db, String table, String key, SqlWrite write) {
        if (db == null || table == null || key == null || write == null) return;
        submit(table, key, db::borrowConnection, write);
    }

    public static void submitPriority(NsukSqliteDatabase db, String table, String key, SqlWrite write) {
        if (db == null || table == null || key == null || write == null) return;
        submitPriority(table, key, () -> executeOwnConnection(db::borrowConnection, write));
    }

    public static boolean submitSync(NsukSqliteDatabase db, String table, String key, SqlWrite write) {
        if (db == null || table == null || key == null || write == null) return false;
        if (isWriterThread()) {
            executeOwnConnection(db::borrowConnection, write);
            return true;
        }
        final boolean[] ok = {false};
        NsukWriteExecutor.submitSync(() -> {
            WRITER_FLAG.set(true);
            try {
                executeOwnConnection(db::borrowConnection, write);
                ok[0] = true;
            } finally {
                WRITER_FLAG.set(false);
            }
        });
        return ok[0];
    }

    private static void executeOwnConnection(ConnectionSupplier supplier, SqlWrite write) {
        try (Connection connection = supplier.get()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                write.write(connection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception exception) {
            NsukAddition.LOGGER.error("WriteBatchBuffer: SQLite write failed", exception);
        }
    }

    public static void flushAll() {
        if (bufferSize.get() == 0) return;
        ConcurrentHashMap<String, ConcurrentHashMap<String, WriteEntry>> snapshot = active;
        active = new ConcurrentHashMap<>();
        bufferSize.set(0);
        executeFlush(snapshot);
    }

    private static void executeFlush(ConcurrentHashMap<String, ConcurrentHashMap<String, WriteEntry>> snapshot) {
        WRITER_FLAG.set(true);
        try {
            for (Map.Entry<String, ConcurrentHashMap<String, WriteEntry>> tableEntry : snapshot.entrySet()) {
                flushTable(tableEntry.getKey(), tableEntry.getValue());
            }
            snapshot.clear();
        } finally {
            WRITER_FLAG.set(false);
            flushingNow.set(false);
        }
    }

    private static int flushTable(String table, ConcurrentHashMap<String, WriteEntry> bucket) {
        if (bucket.isEmpty()) {
            return 0;
        }
        ConnectionSupplier supplier = bucket.values().iterator().next().supplier();
        int count = 0;
        try (Connection connection = supplier.get()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (WriteEntry entry : bucket.values()) {
                    entry.write().write(connection);
                    count++;
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception exception) {
            NsukAddition.LOGGER.error("WriteBatchBuffer: batch flush failed for table={}", table, exception);
        }
        return count;
    }

    public static int getBufferSize() {
        return bufferSize.get();
    }

    public static boolean isFlushing() {
        return flushingNow.get();
    }
}