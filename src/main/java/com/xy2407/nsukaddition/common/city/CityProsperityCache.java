package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.economy.EconomyService;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 城市繁荣度缓存，住宅建筑增删时失效，避免每次收租或HUD同步时全量遍历计算。 */
public final class CityProsperityCache {

    private static final ConcurrentHashMap<UUID, Long> CACHE = new ConcurrentHashMap<>();

    private CityProsperityCache() {}

    public static long getOrCalculate(ServerLevel level, UUID cityId) {
        if (cityId == null) return 0L;
        return CACHE.computeIfAbsent(cityId, k -> calculate(level, k));
    }

    public static void invalidate(UUID cityId) {
        if (cityId != null) CACHE.remove(cityId);
    }

    public static void clearAll() {
        CACHE.clear();
    }

    private static long calculate(ServerLevel level, UUID cityId) {
        double total = 0;
        for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
            if (!cityId.equals(rec.cityId())) continue;
            if (!"residential".equalsIgnoreCase(rec.category())) continue;
            double price = EconomyService.parseAmount(rec.amount(), "residential_rent");
            if (price <= 0) {
                price = BuildingCatalog.findBuilding(rec.category(), rec.buildingFileName())
                        .map(BuildingCatalog.BuildingDefinition::amount)
                        .map(a -> EconomyService.parseAmount(a, "residential_rent"))
                        .orElse(0.0);
            }
            if (price > 0) total += price / 2.0;
        }
        return Math.round(total);
    }
}