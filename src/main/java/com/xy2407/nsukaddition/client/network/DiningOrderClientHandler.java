package com.xy2407.nsukaddition.client.network;

import com.xy2407.nsukaddition.common.network.cooking.DiningOrderSyncPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端就餐订单缓存，存储 NPC→菜品映射及是否为游客标志（供头顶字体着色）。 */
public final class DiningOrderClientHandler {
    private static final Map<UUID, String> ORDERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> TOURIST_FLAGS = new ConcurrentHashMap<>();

    private DiningOrderClientHandler() {}

    public static void handle(DiningOrderSyncPacket p) {
        if (p.start()) {
            ORDERS.put(p.citizenId(), p.itemId());
            TOURIST_FLAGS.put(p.citizenId(), p.isTourist());
        } else {
            ORDERS.remove(p.citizenId());
            TOURIST_FLAGS.remove(p.citizenId());
        }
    }

    public static String getOrderedItem(UUID citizenId) {
        return ORDERS.get(citizenId);
    }

    public static boolean isDining(UUID citizenId) {
        return ORDERS.containsKey(citizenId);
    }

    public static boolean isTouristDining(UUID citizenId) {
        return Boolean.TRUE.equals(TOURIST_FLAGS.get(citizenId));
    }

    public static void clear() {
        ORDERS.clear();
        TOURIST_FLAGS.clear();
    }
}
