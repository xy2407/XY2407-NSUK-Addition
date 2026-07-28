package com.xy2407.nsukaddition.common.network.clientbound;

import net.minecraft.core.BlockPos;

import java.util.function.BiConsumer;

/** 自动补货状态桥接器，通过可替换的消费者解耦公共包对客户端缓存的直接依赖。 */
public final class AutoRestockStateBridge {

    private static BiConsumer<BlockPos, Boolean> handler = (pos, enabled) -> {
    };

    private AutoRestockStateBridge() {
    }

    public static void install(BiConsumer<BlockPos, Boolean> handler) {
        AutoRestockStateBridge.handler = handler != null ? handler : (pos, enabled) -> {
        };
    }

    public static void reset() {
        handler = (pos, enabled) -> {
        };
    }

    public static void handle(BlockPos pos, boolean enabled) {
        handler.accept(pos, enabled);
    }
}
