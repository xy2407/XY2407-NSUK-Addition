package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenResponsePacket;

import java.util.function.Consumer;

/** 繁育控制箱界面桥接，解耦公共包对客户端 BreedingControlBoxScreenOpener 的直接依赖。 */
public final class BreedingControlBoxBridge {

    private static Consumer<BreedingControlBoxOpenResponsePacket> openHandler = p -> {};
    private static Consumer<BreedingControlBoxOpenResponsePacket> refreshHandler = p -> {};

    private BreedingControlBoxBridge() {}

    public static void install(Consumer<BreedingControlBoxOpenResponsePacket> open,
                               Consumer<BreedingControlBoxOpenResponsePacket> refresh) {
        openHandler = open != null ? open : p -> {};
        refreshHandler = refresh != null ? refresh : p -> {};
    }

    public static void reset() {
        openHandler = p -> {};
        refreshHandler = p -> {};
    }

    public static void open(BreedingControlBoxOpenResponsePacket p) {
        openHandler.accept(p);
    }

    public static void refresh(BreedingControlBoxOpenResponsePacket p) {
        refreshHandler.accept(p);
    }
}
