package com.xy2407.nsukaddition.common.citycore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiConsumer;

/** 城市核心放置后触发“村庄转城市”检测的桥接器，解耦 common 放置逻辑与 server 转换逻辑。 */
public final class VillageCityConversionTrigger {

    private static BiConsumer<ServerLevel, BlockPos> handler = (level, pos) -> {
    };

    private VillageCityConversionTrigger() {
    }

    public static void install(BiConsumer<ServerLevel, BlockPos> newHandler) {
        handler = newHandler != null ? newHandler : (level, pos) -> {
        };
    }

    public static void onCorePlaced(ServerLevel level, BlockPos corePos) {
        handler.accept(level, corePos);
    }
}