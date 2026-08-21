package com.xy2407.nsukaddition.common.network.clientbound;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** RTS 选中纠正桥接器，解耦公共包对客户端 RtsModeManager 的直接依赖(客户端安装实现)。 */
public final class RtsSelectionCorrectionBridge {

    private static Consumer<Set<UUID>> handler = ids -> {
    };

    private RtsSelectionCorrectionBridge() {
    }

    public static void install(Consumer<Set<UUID>> handler) {
        RtsSelectionCorrectionBridge.handler = handler != null ? handler : ids -> {
        };
    }

    public static void handle(Set<UUID> ids) {
        handler.accept(ids);
    }
}
