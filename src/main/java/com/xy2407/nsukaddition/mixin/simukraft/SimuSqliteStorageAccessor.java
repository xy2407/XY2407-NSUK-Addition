package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.ConcurrentMap;

/** 暴露 SimuSqliteStorage 的静态实例缓存与 database 字段，供 nsuk 直接操作 simukraft 库的 building_tasks 表。 */
@Mixin(value = SimuSqliteStorage.class, remap = false)
public interface SimuSqliteStorageAccessor {

    @Accessor("STORAGES")
    static ConcurrentMap<MinecraftServer, SimuSqliteStorage> nsuk$getStorages() {
        throw new AssertionError();
    }

    @Accessor("database")
    SimuSqliteDatabase nsuk$getDatabase();
}
