package com.xy2407.nsukaddition.common.network.clientbound;

/**
 * 建筑界限清空桥：解耦公共包对客户端类(BuildingBoundsRenderer.clearAll)的直接依赖。
 * 客户端 NsukAdditionClient 中 install，收到清空信号时清空已显示的建筑界限。
 */
public final class RtsBuildingBoundsClearBridge {

    private static Runnable handler = () -> {
    };

    private RtsBuildingBoundsClearBridge() {
    }

    public static void install(Runnable h) {
        handler = h != null ? h : () -> {
        };
    }

    public static void dispatch() {
        handler.run();
    }
}
