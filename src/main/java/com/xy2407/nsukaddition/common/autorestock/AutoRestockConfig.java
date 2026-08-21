package com.xy2407.nsukaddition.common.autorestock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 自动补货功能的运行时配置，管理启用位置集合并持久化到 SQLite。 */
public final class AutoRestockConfig {

    private static final ConcurrentHashMap.KeySetView<BlockPos, Boolean> ENABLED =
            ConcurrentHashMap.newKeySet();

    private AutoRestockConfig() {}

    public static void loadFromDatabase(ServerLevel level) {
        ENABLED.clear();
        Set<BlockPos> loaded = AutoRestockSqliteStorage.loadAll(level);
        ENABLED.addAll(loaded);
    }

    public static void clear() {
        ENABLED.clear();
    }

    public static boolean isEnabled(BlockPos pos) {
        return pos != null && ENABLED.contains(pos.immutable());
    }

    public static Set<BlockPos> allEnabled() {
        return Set.copyOf(ENABLED);
    }

    public static void setEnabled(ServerLevel level, BlockPos pos, boolean enabled) {
        if (pos == null) return;
        BlockPos key = pos.immutable();
        if (enabled) {
            if (ENABLED.add(key)) {
                AutoRestockSqliteStorage.save(level, key);
            }
        } else {
            if (ENABLED.remove(key)) {
                AutoRestockSqliteStorage.delete(level, key);
            }
        }
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        if (pos == null) return;
        if (ENABLED.remove(pos.immutable())) {
            AutoRestockSqliteStorage.delete(level, pos);
        }
    }
}
