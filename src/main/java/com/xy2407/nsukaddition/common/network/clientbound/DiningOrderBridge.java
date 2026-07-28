package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.cooking.DiningOrderSyncPacket;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 就餐订单桥接，解耦公共包对客户端 DiningOrderClientHandler 的直接依赖。 */
public final class DiningOrderBridge {

    private static Consumer<DiningOrderSyncPacket> handler = p -> {};

    private DiningOrderBridge() {}

    public static void install(Consumer<DiningOrderSyncPacket> h) {
        handler = h != null ? h : p -> {};
    }

    public static void reset() { handler = p -> {}; }

    public static void handle(DiningOrderSyncPacket p) { handler.accept(p); }
}
