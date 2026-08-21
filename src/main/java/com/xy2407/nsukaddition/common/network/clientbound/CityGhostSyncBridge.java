package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.citycore.CityGhostSyncPacket;

import java.util.List;
import java.util.function.Consumer;

/** 建筑任务虚影同步桥接器，解耦公共包对客户端渲染的直接依赖。 */
public final class CityGhostSyncBridge {

    private static Consumer<List<CityGhostSyncPacket.GhostTaskInfo>> handler = infos -> {
    };

    private CityGhostSyncBridge() {
    }

    public static void install(Consumer<List<CityGhostSyncPacket.GhostTaskInfo>> h) {
        handler = h != null ? h : infos -> {
        };
    }

    public static void handle(List<CityGhostSyncPacket.GhostTaskInfo> infos) {
        handler.accept(infos);
    }
}
