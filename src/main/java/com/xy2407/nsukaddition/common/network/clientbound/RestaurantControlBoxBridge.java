package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxOpenResponsePacket;

import java.util.function.Consumer;

/** 餐厅控制箱界面桥接，解耦公共包对客户端 RestaurantControlBoxScreenOpener 的直接依赖。 */
public final class RestaurantControlBoxBridge {

    private static Consumer<RestaurantControlBoxOpenResponsePacket> openHandler = p -> {};
    private static Consumer<RestaurantControlBoxOpenResponsePacket> refreshHandler = p -> {};

    private RestaurantControlBoxBridge() {}

    public static void install(Consumer<RestaurantControlBoxOpenResponsePacket> open,
                               Consumer<RestaurantControlBoxOpenResponsePacket> refresh) {
        openHandler = open != null ? open : p -> {};
        refreshHandler = refresh != null ? refresh : p -> {};
    }

    public static void reset() {
        openHandler = p -> {};
        refreshHandler = p -> {};
    }

    public static void open(RestaurantControlBoxOpenResponsePacket p) { openHandler.accept(p); }
    public static void refresh(RestaurantControlBoxOpenResponsePacket p) { refreshHandler.accept(p); }
}
