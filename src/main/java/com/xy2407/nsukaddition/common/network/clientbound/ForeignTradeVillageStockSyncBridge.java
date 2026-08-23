package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeVillageStockSyncPacket.StockInfo;

import java.util.Map;
import java.util.function.Consumer;

/** 村庄库存同步桥接，解耦公共包对客户端Minecraft类的直接依赖。 */
public final class ForeignTradeVillageStockSyncBridge {

    private static Consumer<Map<String, StockInfo>> handler = stocks -> {};

    private ForeignTradeVillageStockSyncBridge() {}

    public static void install(Consumer<Map<String, StockInfo>> h) {
        handler = h != null ? h : stocks -> {};
    }

    public static void reset() {
        handler = stocks -> {};
    }

    public static void handleSync(Map<String, StockInfo> stocks) {
        handler.accept(stocks != null ? stocks : Map.of());
    }
}