package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 扩展SimuSqliteDatabase：提升busy_timeout防止写锁等待超时。 */
@Mixin(SimuSqliteDatabase.class)
public class SimuSqliteDatabaseMixin {

    @Inject(method = "openConnection", at = @At("RETURN"))
    private void extendBusyTimeout(CallbackInfoReturnable<Connection> cir) {
        Connection conn = cir.getReturnValue();
        if (conn == null) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=30000");
        } catch (SQLException ignored) {
        }
    }
}
