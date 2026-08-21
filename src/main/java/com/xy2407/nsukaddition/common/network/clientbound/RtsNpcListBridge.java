package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.rts.RtsNpcListPacket;

import java.util.function.Consumer;

/** RTS NPC 列表桥接器，解耦公共包对客户端 RtsNpcCache 的直接依赖(客户端安装实现)。 */
public final class RtsNpcListBridge {

    private static Consumer<RtsNpcListPacket> handler = packet -> {
    };

    private RtsNpcListBridge() {
    }

    public static void install(Consumer<RtsNpcListPacket> handler) {
        RtsNpcListBridge.handler = handler != null ? handler : packet -> {
        };
    }

    public static void handle(RtsNpcListPacket packet) {
        handler.accept(packet);
    }
}
