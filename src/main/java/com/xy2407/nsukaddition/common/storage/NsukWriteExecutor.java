package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.NsukAddition;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 异步写入执行器，所有SQLite写操作提交到此单线程队列串行执行，彻底避免SQLITE_BUSY。 */
@SuppressWarnings("null")
public final class NsukWriteExecutor {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NSukAddition-SQLite-Writer-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private NsukWriteExecutor() {}

    // 提交写任务到异步队列。调用者无需再加锁。
    public static void submit(Runnable task) {
        EXECUTOR.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Async SQLite write failed", e);
            }
        });
    }

    // 同步执行写任务（在调用者线程），用于需要立即返回结果的场景。
    public static void submitSync(Runnable task) {
        try {
            EXECUTOR.submit(task).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Sync SQLite write failed", e);
            throw new RuntimeException("Sync SQLite write timeout or error", e);
        }
    }

    // 服务器关闭时优雅关闭写入线程。
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
