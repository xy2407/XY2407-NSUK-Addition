package com.xy2407.nsukaddition.client.autorestock;

import net.minecraft.core.BlockPos;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端自动补货状态缓存，由服务端 S→C 包同步。 */
public final class ClientAutoRestockCache {

    private static final ConcurrentHashMap.KeySetView<BlockPos, Boolean> ENABLED =
            ConcurrentHashMap.newKeySet();

    private ClientAutoRestockCache() {}

    public static boolean isEnabled(BlockPos pos) {
        return pos != null && ENABLED.contains(pos.immutable());
    }

    public static void set(BlockPos pos, boolean enabled) {
        if (pos == null) return;
        BlockPos key = pos.immutable();
        if (enabled) ENABLED.add(key);
        else ENABLED.remove(key);
    }

    public static void clear() {
        ENABLED.clear();
    }
}
