package com.xy2407.nsukaddition.common.city;

import com.xy2407.nsukaddition.common.network.city.CityCorePositionsPacket.CoreInfo;

import java.util.List;

/** 客户端缓存当前维度的城市核心位置及归属信息，供发光轮廓渲染器读取。 */
public final class CityCorePositionsCache {

    private static List<CoreInfo> cores = List.of();

    private CityCorePositionsCache() {}

    public static void update(List<CoreInfo> newCores) {
        cores = List.copyOf(newCores);
    }

    public static List<CoreInfo> getCores() {
        return cores;
    }

    public static void clear() {
        cores = List.of();
    }
}
