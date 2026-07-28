package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeInventorySyncPacket;

import java.util.Map;
import java.util.function.Consumer;

/** 物流仓库物品数量同步桥接，解耦公共包对客户端Minecraft类的直接依赖。 */
public final class ForeignTradeInventorySyncBridge {

    private static Consumer<Map<String, Integer>> handler = counts -> {};

    private ForeignTradeInventorySyncBridge() {}

    public static void install(Consumer<Map<String, Integer>> h) {
        handler = h != null ? h : counts -> {};
    }

    public static void reset() {
        handler = counts -> {};
    }

    public static void handleSync(Map<String, Integer> counts) {
        handler.accept(counts);
    }
}
