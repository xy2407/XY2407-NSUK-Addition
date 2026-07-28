package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeMarketDataPacket;

import java.util.List;
import java.util.function.BiConsumer;

/** 外贸市场数据桥接，解耦公共包对客户端ScreenOpener的直接依赖。 */
public final class ForeignTradeMarketDataBridge {

    private static MarketDataHandler handler = (pos, entries, canOperate) -> {};

    private ForeignTradeMarketDataBridge() {}

    @FunctionalInterface
    public interface MarketDataHandler {
        void handle(net.minecraft.core.BlockPos pos, List<ForeignTradeMarket.MarketEntry> entries, boolean canOperate);
    }

    public static void install(MarketDataHandler h) {
        handler = h != null ? h : (pos, entries, canOperate) -> {};
    }

    public static void reset() {
        handler = (pos, entries, canOperate) -> {};
    }

    public static void handleData(net.minecraft.core.BlockPos pos, List<ForeignTradeMarket.MarketEntry> entries, boolean canOperate) {
        handler.handle(pos, entries, canOperate);
    }
}
