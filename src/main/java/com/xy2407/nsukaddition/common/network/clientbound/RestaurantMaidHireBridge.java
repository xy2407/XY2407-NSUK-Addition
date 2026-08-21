package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireResponsePacket;

import java.util.function.Consumer;

/** 餐厅女仆雇佣界面桥接，解耦公共包对客户端 RestaurantMaidHireScreenOpener 的直接依赖。 */
public final class RestaurantMaidHireBridge {

    private static Consumer<RestaurantMaidHireResponsePacket> openHandler = p -> {};

    private RestaurantMaidHireBridge() {}

    public static void install(Consumer<RestaurantMaidHireResponsePacket> open) {
        openHandler = open != null ? open : p -> {};
    }

    public static void reset() {
        openHandler = p -> {};
    }

    public static void open(RestaurantMaidHireResponsePacket p) { openHandler.accept(p); }
}
