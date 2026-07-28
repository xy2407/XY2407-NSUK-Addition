package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.storage.PooledConnectionHandler;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** 通过重定向DriverManager.getConnection实现SQLite连接复用，消除7.53%连接创建开销。 */
@Mixin(SimuSqliteDatabase.class)
public class SimuSqliteConnectionPoolMixin {

    @Unique
    private Connection nsuk$cachedConnection;

    @Unique
    private Connection nsuk$proxyConnection;

    @Redirect(method = "openConnection",
            at = @At(value = "INVOKE",
                    target = "Ljava/sql/DriverManager;getConnection(Ljava/lang/String;)Ljava/sql/Connection;"),
            remap = false)
    private Connection nsuk$getOrCacheConnection(String url) throws SQLException {
        synchronized (this) {
            Connection cached = nsuk$cachedConnection;
            if (cached != null && !cached.isClosed()) {
                return nsuk$proxyConnection;
            }
            Connection real = DriverManager.getConnection(url);
            nsuk$cachedConnection = real;
            nsuk$proxyConnection = PooledConnectionHandler.wrap(real);
            return nsuk$proxyConnection;
        }
    }
}
