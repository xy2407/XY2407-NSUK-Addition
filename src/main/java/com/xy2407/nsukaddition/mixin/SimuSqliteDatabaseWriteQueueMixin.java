package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import common.cn.kafei.simukraft.storage.core.SqlFunction;
import common.cn.kafei.simukraft.storage.core.SqlWrite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接管官方 SimuSqliteDatabase 的两个 submitWrite 入口：
 * 带合并键的写入转 WriteBatchBuffer 20 tick 批量缓冲（按表分桶、key 去重）；
 * 无合并键的集合写（saveAll 等）保证顺序，立即提交到写线程执行。
 * 官方 StorageWriteQueue 不再收到任何写提交，彻底避免双写线程竞争 SQLITE_BUSY。
 */
@Mixin(value = SimuSqliteDatabase.class, remap = false)
public abstract class SimuSqliteDatabaseWriteQueueMixin {

    private static final AtomicLong ORDERED_SEQ = new AtomicLong();

    @Inject(method = "submitWrite(Ljava/lang/Object;Lcommon/cn/kafei/simukraft/storage/core/SqlWrite;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$bufferKeyedWrite(Object key, SqlWrite write, CallbackInfo ci) {
        if (WriteBatchBuffer.isWriterThread()) {
            return;
        }
        if (key == null || write == null) {
            ci.cancel();
            return;
        }
        SimuSqliteDatabase self = (SimuSqliteDatabase) (Object) this;
        if (self.isDegraded()) {
            NsukAddition.LOGGER.warn("WriteBatchBuffer: SQLite degraded, dropping keyed write {}", key);
            ci.cancel();
            return;
        }
        if (WriteBatchBuffer.isImmediate()) {
            WriteBatchBuffer.submitPriority(tableOf(key), String.valueOf(key), () -> executeWrite(self, write));
            ci.cancel();
            return;
        }
        WriteBatchBuffer.submit(tableOf(key), String.valueOf(key), self::borrowConnection, write);
        ci.cancel();
    }

    @Inject(method = "submitWrite(Lcommon/cn/kafei/simukraft/storage/core/SqlWrite;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$bufferOrderedWrite(SqlWrite write, CallbackInfo ci) {
        if (WriteBatchBuffer.isWriterThread()) {
            return;
        }
        if (write == null) {
            ci.cancel();
            return;
        }
        SimuSqliteDatabase self = (SimuSqliteDatabase) (Object) this;
        if (self.isDegraded()) {
            NsukAddition.LOGGER.warn("WriteBatchBuffer: SQLite degraded, dropping ordered write");
            ci.cancel();
            return;
        }
        WriteBatchBuffer.submitPriority("simukraft", "ordered:" + ORDERED_SEQ.incrementAndGet(),
                () -> executeWrite(self, write));
        ci.cancel();
    }

    @Inject(method = "callSync(Lcommon/cn/kafei/simukraft/storage/core/SqlFunction;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$syncCall(SqlFunction function, CallbackInfoReturnable<Object> cir) {
        SimuSqliteDatabase self = (SimuSqliteDatabase) (Object) this;
        if (WriteBatchBuffer.isWriterThread()) {
            cir.setReturnValue(executeSync(self, function));
            return;
        }
        if (self.isDegraded()) {
            NsukAddition.LOGGER.warn("WriteBatchBuffer: SQLite degraded, callSync returns null");
            cir.setReturnValue(null);
            return;
        }
        final Object[] result = {null};
        NsukWriteExecutor.submitSync(() -> {
            WriteBatchBuffer.enterWriter();
            try {
                result[0] = executeSync(self, function);
            } finally {
                WriteBatchBuffer.exitWriter();
            }
        });
        cir.setReturnValue(result[0]);
    }

    private static Object executeSync(SimuSqliteDatabase db, SqlFunction function) {
        try (Connection connection = db.borrowConnection()) {
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
            NsukAddition.LOGGER.error("WriteBatchBuffer: callSync failed", exception);
            return null;
        }
    }

    private static String tableOf(Object key) {
        String k = String.valueOf(key);
        int idx = k.indexOf(':');
        return idx > 0 ? k.substring(0, idx) : "simukraft";
    }

    private static void executeWrite(SimuSqliteDatabase db, SqlWrite write) {
        try (Connection connection = db.borrowConnection()) {
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

    @Inject(method = "close", at = @At("HEAD"), remap = false)
    private void nsuk$flushBeforePrimaryDbClose(CallbackInfo ci) {
        NsukWriteExecutor.submitSync(() -> {
        });
        WriteBatchBuffer.flushAll();
    }
}
