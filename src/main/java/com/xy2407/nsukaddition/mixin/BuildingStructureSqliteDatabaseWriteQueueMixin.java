package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import common.cn.kafei.simukraft.storage.BuildingStructureSqliteDatabase;
import common.cn.kafei.simukraft.storage.core.SqlFunction;
import common.cn.kafei.simukraft.storage.core.SqlWrite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.sql.Connection;
import java.sql.SQLException;

/** 接管建筑结构库 BuildingStructureSqliteDatabase.submitAsync 与 callSync，使 simukraft_buildings.sqlite 的写统一走批量缓冲/唯一写线程。 */
@Mixin(value = BuildingStructureSqliteDatabase.class, remap = false)
public abstract class BuildingStructureSqliteDatabaseWriteQueueMixin {

    @Inject(method = "submitAsync(Ljava/lang/Object;Lcommon/cn/kafei/simukraft/storage/core/SqlWrite;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$bufferStructureWrite(Object key, SqlWrite write, CallbackInfo ci) {
        if (WriteBatchBuffer.isWriterThread()) {
            return;
        }
        if (key == null || write == null) {
            ci.cancel();
            return;
        }
        BuildingStructureSqliteDatabase self = (BuildingStructureSqliteDatabase) (Object) this;
        if (self.isWriteBlocked()) {
            NsukAddition.LOGGER.warn("WriteBatchBuffer: buildings SQLite write-blocked, dropping key {}", key);
            ci.cancel();
            return;
        }
        WriteBatchBuffer.submit("buildings", String.valueOf(key), self::borrowConnection, write);
        ci.cancel();
    }

    @Inject(method = "callSync(Lcommon/cn/kafei/simukraft/storage/core/SqlFunction;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$syncCall(SqlFunction function, CallbackInfoReturnable<Object> cir) {
        BuildingStructureSqliteDatabase self = (BuildingStructureSqliteDatabase) (Object) this;
        if (WriteBatchBuffer.isWriterThread()) {
            cir.setReturnValue(executeSync(self, function));
            return;
        }
        if (self.isWriteBlocked()) {
            NsukAddition.LOGGER.warn("WriteBatchBuffer: buildings SQLite write-blocked, callSync returns null");
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

    private static Object executeSync(BuildingStructureSqliteDatabase db, SqlFunction function) {
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
            NsukAddition.LOGGER.error("WriteBatchBuffer: buildings callSync failed", exception);
            return null;
        }
    }
}
