package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.rts.RtsStartBuildingResultPacket;

import java.util.function.Consumer;

/**
 * RTS 建筑放置注入结果桥接:解耦公共包对客户端类的直接依赖。
 */
public final class RtsStartBuildingResultBridge {

    private static Consumer<RtsStartBuildingResultPacket> handler = p -> {};

    private RtsStartBuildingResultBridge() {
    }

    public static void install(Consumer<RtsStartBuildingResultPacket> h) {
        handler = h != null ? h : p -> {};
    }

    public static void dispatch(RtsStartBuildingResultPacket packet) {
        handler.accept(packet);
    }
}
