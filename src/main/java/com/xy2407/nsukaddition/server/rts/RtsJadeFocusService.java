package com.xy2407.nsukaddition.server.rts;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录 RTS 玩家准星对准的实体 UUID，供 Jade 服务端距离检查放行。
 * 只放行准星目标实体的 NBT 请求，其余仍受原距离限制——避免全量放宽导致服务器带宽/负载问题。
 */
public final class RtsJadeFocusService {

    private static final Map<UUID, UUID> FOCUS = new ConcurrentHashMap<>();

    private RtsJadeFocusService() {
    }

    /** 全零 UUID：表示清除准星目标。 */
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    /** 设置/清除准星目标(全零 UUID 或 null 表示清除)。 */
    public static void setFocus(UUID player, UUID entity) {
        if (entity == null || entity.equals(ZERO_UUID)) {
            FOCUS.remove(player);
        } else {
            FOCUS.put(player, entity);
        }
    }

    /** 该实体是否为该玩家的准星目标。 */
    public static boolean isFocused(UUID player, UUID entity) {
        return FOCUS.containsKey(player) && FOCUS.get(player).equals(entity);
    }
}
