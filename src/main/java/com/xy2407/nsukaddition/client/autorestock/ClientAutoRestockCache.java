package com.xy2407.nsukaddition.client.autorestock;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 客户端自动补货状态缓存，由服务端 S→C 包同步，支持状态变更回调通知 UI 刷新。 */
public final class ClientAutoRestockCache {

    private static final ConcurrentHashMap.KeySetView<BlockPos, Boolean> ENABLED =
            ConcurrentHashMap.newKeySet();

    private static final List<StateChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientAutoRestockCache() {}

    public static boolean isEnabled(BlockPos pos) {
        return pos != null && ENABLED.contains(pos.immutable());
    }

    public static void set(BlockPos pos, boolean enabled) {
        if (pos == null) return;
        BlockPos key = pos.immutable();
        boolean changed;
        if (enabled) {
            changed = ENABLED.add(key);
        } else {
            changed = ENABLED.remove(key);
        }
        if (changed) {
            for (StateChangeListener listener : LISTENERS) {
                listener.onAutoRestockStateChanged(key, enabled);
            }
        }
    }

    public static void setFromServer(BlockPos pos, boolean enabled) {
        if (pos == null) return;
        BlockPos key = pos.immutable();
        if (enabled) {
            ENABLED.add(key);
        } else {
            ENABLED.remove(key);
        }
        for (StateChangeListener listener : LISTENERS) {
            listener.onAutoRestockStateChanged(key, enabled);
        }
    }

    public static void addListener(StateChangeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(StateChangeListener listener) {
        LISTENERS.remove(listener);
    }

    public static void clear() {
        ENABLED.clear();
    }

    @FunctionalInterface
    public interface StateChangeListener {
        void onAutoRestockStateChanged(BlockPos pos, boolean enabled);
    }
}
