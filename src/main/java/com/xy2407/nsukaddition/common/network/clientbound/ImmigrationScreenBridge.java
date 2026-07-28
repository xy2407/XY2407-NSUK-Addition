package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.city.ImmigrantData;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** 移民界面桥接，解耦公共包对客户端 ImmigrationScreen 的直接依赖。 */
public final class ImmigrationScreenBridge {

    private static BiConsumer<UUID, List<ImmigrantData>> handler = (cityId, immigrants) -> {};

    private ImmigrationScreenBridge() {}

    public static void install(BiConsumer<UUID, List<ImmigrantData>> handler) {
        ImmigrationScreenBridge.handler = handler != null ? handler : (cityId, immigrants) -> {};
    }

    public static void reset() {
        handler = (cityId, immigrants) -> {};
    }

    public static void refresh(UUID cityId, List<ImmigrantData> immigrants) {
        handler.accept(cityId, immigrants);
    }
}
