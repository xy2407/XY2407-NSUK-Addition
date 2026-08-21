package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;

import java.util.List;
import java.util.function.Consumer;

/** 外交数据桥接，解耦公共包对客户端Minecraft类的直接依赖。 */
public final class DiplomacyDataBridge {

    private DiplomacyDataBridge() {}

    private static Consumer<List<DiplomacyRelation>> handler = list -> {};

    public static void install(Consumer<List<DiplomacyRelation>> h) {
        handler = h != null ? h : list -> {};
    }

    public static void reset() {
        handler = list -> {};
    }

    public static void handleData(List<DiplomacyRelation> relations) {
        handler.accept(relations);
    }
}
