package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.rts.RtsPlacedBuildingSyncPacket;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 已放置建筑同步桥：解耦公共包对客户端类的直接依赖。
 */
public final class RtsPlacedBuildingSyncBridge {

    private static BiConsumer<List<RtsPlacedBuildingSyncPacket.Entry>, List<Long>> handler = (b, c) -> {};

    private RtsPlacedBuildingSyncBridge() {
    }

    public static void install(BiConsumer<List<RtsPlacedBuildingSyncPacket.Entry>, List<Long>> h) {
        handler = h != null ? h : (b, c) -> {};
    }

    public static void dispatch(List<RtsPlacedBuildingSyncPacket.Entry> buildings, List<Long> cityChunks) {
        handler.accept(buildings, cityChunks);
    }
}
