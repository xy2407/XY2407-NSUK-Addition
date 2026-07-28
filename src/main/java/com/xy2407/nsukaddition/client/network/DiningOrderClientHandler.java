package com.xy2407.nsukaddition.client.network;

import com.xy2407.nsukaddition.common.network.cooking.DiningOrderSyncPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端就餐订单缓存，存储 NPC→菜品映射。 */
public final class DiningOrderClientHandler {
    private static final Map<UUID, String> ORDERS = new ConcurrentHashMap<>();

    private DiningOrderClientHandler() {}

    public static void handle(DiningOrderSyncPacket p) {
        if (p.start()) {
            ORDERS.put(p.citizenId(), p.itemId());
        } else {
            ORDERS.remove(p.citizenId());
        }
    }

    public static String getOrderedItem(UUID citizenId) {
        return ORDERS.get(citizenId);
    }

    public static boolean isDining(UUID citizenId) {
        return ORDERS.containsKey(citizenId);
    }

    public static void clear() {
        ORDERS.clear();
    }
}
