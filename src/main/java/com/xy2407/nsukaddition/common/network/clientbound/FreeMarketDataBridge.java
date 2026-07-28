package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;

import java.util.List;
import java.util.function.BiConsumer;

/** 自由市场数据桥接，解耦公共包对客户端Minecraft类的直接依赖。 */
public final class FreeMarketDataBridge {

    private static BiConsumer<List<FreeMarketRepository.FreeMarketListing>, List<FreeMarketRepository.FreeMarketListing>> handler = (a, b) -> {};

    private FreeMarketDataBridge() {}

    public static void install(BiConsumer<List<FreeMarketRepository.FreeMarketListing>, List<FreeMarketRepository.FreeMarketListing>> h) {
        handler = h != null ? h : (a, b) -> {};
    }

    public static void reset() {
        handler = (a, b) -> {};
    }

    public static void handleData(List<FreeMarketRepository.FreeMarketListing> ownCity, List<FreeMarketRepository.FreeMarketListing> otherCity) {
        handler.accept(ownCity, otherCity);
    }
}
