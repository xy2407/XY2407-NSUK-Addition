package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;

import java.util.function.Consumer;

/** 侧边栏同步桥接器，通过可替换的消费者解耦公共包对客户端界面的直接依赖。 */
public final class SidebarSyncBridge {

    private static Consumer<SidebarSyncPacket> handler = packet -> {

    };

    private SidebarSyncBridge() {
    }

    public static void install(Consumer<SidebarSyncPacket> handler) {
        SidebarSyncBridge.handler = handler != null ? handler : packet -> {
        };
    }

    public static void reset() {
        handler = packet -> {
        };
    }

    public static void handle(SidebarSyncPacket packet) {
        handler.accept(packet);
    }
}
