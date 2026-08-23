package com.xy2407.nsukaddition.common.rts;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** RTS 期间被服务端驱动移动的玩家UUID集合，供实体推挤/行为过滤共同查询。 */
public final class RtsPlayerState {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private RtsPlayerState() {
    }

    public static void mark(UUID id) {
        if (id != null) ACTIVE.add(id);
    }

    public static void clear(UUID id) {
        if (id != null) ACTIVE.remove(id);
    }

    public static boolean isActive(UUID id) {
        return id != null && ACTIVE.contains(id);
    }

    public static Set<UUID> all() {
        return ACTIVE;
    }
}