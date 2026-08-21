package com.xy2407.nsukaddition.common.industrial;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** jump 步骤期间临时禁用 rescueFromWall 传送的标志持有器。 */
public final class JumpRescueSkipHolder {
    private static final Set<UUID> SKIP_IDS = ConcurrentHashMap.newKeySet();

    private JumpRescueSkipHolder() {
    }

    public static void setSkip(UUID entityId, boolean skip) {
        if (skip) {
            SKIP_IDS.add(entityId);
        } else {
            SKIP_IDS.remove(entityId);
        }
    }

    public static boolean shouldSkip(UUID entityId) {
        return SKIP_IDS.contains(entityId);
    }

    public static void clearAll() {
        SKIP_IDS.clear();
    }
}
