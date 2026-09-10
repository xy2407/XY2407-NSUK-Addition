package com.xy2407.nsukaddition.common.network.clientbound;

import java.util.UUID;
import java.util.function.Consumer;

/** 市民就餐忙碌提示的客户端分发桥，由客户端在初始化时安装实际处理（人口界面显示提示）。 */
public final class CitizenBusyBridge {

    private static volatile Consumer<UUID> handler;

    private CitizenBusyBridge() {
    }

    public static void install(Consumer<UUID> h) {
        handler = h;
    }

    public static void dispatch(UUID uuid) {
        Consumer<UUID> h = handler;
        if (h != null) {
            h.accept(uuid);
        }
    }
}