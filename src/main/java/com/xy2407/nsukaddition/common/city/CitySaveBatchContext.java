package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 城市保存批处理上下文：collectRentForDay 期间跳过逐次 saveCityIncremental，结束时统一保存。 */
public final class CitySaveBatchContext {

    /** 批处理模式标志：开启时跳过 saveCityIncremental。 */
    private static volatile boolean batchMode = false;

    /** 批处理期间待保存的城市ID集合（去重，避免同一城市重复保存）。 */
    private static final Set<UUID> pendingCities = ConcurrentHashMap.newKeySet();

    private CitySaveBatchContext() {}

    /** 是否处于批处理模式。 */
    public static boolean isInBatchMode() {
        return batchMode;
    }

    /** 记录待保存的城市ID（由 Mixin 在跳过 saveCityIncremental 时调用）。 */
    public static void markPending(UUID cityId) {
        pendingCities.add(cityId);
    }

    /** 开启批处理模式：清空待保存集合。 */
    public static void startBatch() {
        batchMode = true;
        pendingCities.clear();
    }

    /** 结束批处理模式：统一保存所有待保存城市，将4K次写入降为K次。 */
    public static void endBatch(ServerLevel level) {
        batchMode = false;
        if (level == null || pendingCities.isEmpty()) {
            pendingCities.clear();
            return;
        }
        CityManager manager = CityManager.get(level);
        for (CityData city : manager.allCities()) {
            if (pendingCities.contains(city.cityId())) {
                SimuSqliteStorage.saveCity(level, city.toTag());
            }
        }
        pendingCities.clear();
    }
}
