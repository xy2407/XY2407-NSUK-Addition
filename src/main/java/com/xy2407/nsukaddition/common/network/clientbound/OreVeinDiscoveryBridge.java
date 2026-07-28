package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryResponsePacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

/** 矿脉发现响应桥接，解耦公共包对客户端 OreVeinDiscoveryClientHandler 的直接依赖。 */
public final class OreVeinDiscoveryBridge {

    private static BiConsumer<OreVeinDiscoveryResponsePacket, IPayloadContext> handler = (p, ctx) -> {};

    private OreVeinDiscoveryBridge() {}

    public static void install(BiConsumer<OreVeinDiscoveryResponsePacket, IPayloadContext> handler) {
        OreVeinDiscoveryBridge.handler = handler != null ? handler : (p, ctx) -> {};
    }

    public static void reset() {
        handler = (p, ctx) -> {};
    }

    public static void handle(OreVeinDiscoveryResponsePacket p, IPayloadContext ctx) {
        handler.accept(p, ctx);
    }
}
