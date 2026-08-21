package com.xy2407.nsukaddition.client.rts;

import com.xy2407.nsukaddition.common.network.rts.RtsNpcListPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** RTS 专属 NPC 归属缓存：服务端推送的玩家所属城市市民列表(UUID→职业)，RTS 选中/建筑模式判断专用，与 HUD 快照完全隔离。 */
public final class RtsNpcCache {

    private static volatile UUID cityId = null;
    private static volatile Map<UUID, String> npcs = Map.of();

    private RtsNpcCache() {
    }

    public static void apply(RtsNpcListPacket packet) {
        if (packet == null) {
            return;
        }
        Map<UUID, String> map = new HashMap<>();
        for (RtsNpcListPacket.NpcEntry e : packet.npcs()) {
            if (e.uuid() != null) {
                map.put(e.uuid(), e.jobType() != null ? e.jobType() : "");
            }
        }
        cityId = packet.cityId();
        npcs = Map.copyOf(map);
    }

    public static boolean isReady() {
        return cityId != null;
    }

    public static UUID getCityId() {
        return cityId;
    }

    public static boolean isOwnCityNpc(UUID id) {
        return id != null && npcs.containsKey(id);
    }

    public static boolean isBuilder(UUID id) {
        return id != null && "BUILDER".equalsIgnoreCase(npcs.getOrDefault(id, ""));
    }
}