package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketWarehouseRequestPacket;

import java.util.List;
import java.util.function.Consumer;

/** 物流仓库物品清单桥接，解耦公共包对客户端Minecraft类的直接依赖。 */
public final class FreeMarketWarehouseDataBridge {

    private static Consumer<List<FreeMarketWarehouseRequestPacket.WarehouseItem>> handler = items -> {};

    private FreeMarketWarehouseDataBridge() {}

    public static void install(Consumer<List<FreeMarketWarehouseRequestPacket.WarehouseItem>> h) {
        handler = h != null ? h : items -> {};
    }

    public static void reset() {
        handler = items -> {};
    }

    public static void handleData(List<FreeMarketWarehouseRequestPacket.WarehouseItem> items) {
        handler.accept(items);
    }
}
