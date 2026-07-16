package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenResponsePacket;

import java.util.function.Consumer;

/** 外贸控制箱界面桥接，解耦公共包对客户端ScreenOpener的直接依赖。 */
public final class ForeignTradeControlBoxBridge {

    private static Consumer<ForeignTradeControlBoxOpenResponsePacket> openHandler = p -> {};

    private ForeignTradeControlBoxBridge() {}

    public static void install(Consumer<ForeignTradeControlBoxOpenResponsePacket> open) {
        openHandler = open != null ? open : p -> {};
    }

    public static void reset() {
        openHandler = p -> {};
    }

    public static void open(ForeignTradeControlBoxOpenResponsePacket p) {
        openHandler.accept(p);
    }
}
