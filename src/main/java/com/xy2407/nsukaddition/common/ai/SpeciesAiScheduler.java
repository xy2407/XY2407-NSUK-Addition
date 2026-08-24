package com.xy2407.nsukaddition.common.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EntityType;

/** 按生物类型节流高频 AI：每 40tick 窗口内每种生物仅放行 2 个实体执行，其余冻结等待。 */
public final class SpeciesAiScheduler {
    public static final int WINDOW_TICKS = 40;
    public static final int BUDGET_PER_WINDOW = 2;

    private static final Map<EntityType<?>, Window> WINDOWS = new ConcurrentHashMap<>();

    private SpeciesAiScheduler() {
    }

    private static final class Window {
        long windowId = -1L;
        int granted = 0;
    }

    public static boolean tryGrant(EntityType<?> type, long gameTick) {
        Window window = WINDOWS.computeIfAbsent(type, t -> new Window());
        long id = gameTick / WINDOW_TICKS;
        if (window.windowId != id) {
            window.windowId = id;
            window.granted = 0;
        }
        if (window.granted >= BUDGET_PER_WINDOW) {
            return false;
        }
        window.granted++;
        return true;
    }
}