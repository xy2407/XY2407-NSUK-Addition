package com.xy2407.nsukaddition.common.city;

import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 非玩家 NPC 识别辅助：游客/商队/村庄城市 NPC 均不参与玩家核心玩法，
 * 各精简 Mixin 据此跳过其 SimuKraft 居民逻辑，仅保留必要的移动与交易。
 */
public final class TouristNpcHelper {

    private static final ConcurrentMap<UUID, Boolean> VILLAGE_CITY_CACHE = new ConcurrentHashMap<>();

    private TouristNpcHelper() {
    }

    public static boolean isTouristEntity(CitizenEntity entity) {
        return entity != null && entity.getTags().contains(TourismConstants.TOURIST_TAG);
    }

    public static boolean isCaravanEntity(CitizenEntity entity) {
        return entity != null && entity.getTags().contains(TourismConstants.CARAVAN_TAG);
    }

    public static boolean isTradeEntity(CitizenEntity entity) {
        return entity != null && entity.getTags().contains(TourismConstants.TRADE_TAG);
    }

    public static boolean isVillageCityNpc(CitizenData data, ServerLevel level) {
        if (data == null || data.cityId() == null) {
            return false;
        }
        UUID cityId = data.cityId();
        Boolean cached = VILLAGE_CITY_CACHE.get(cityId);
        if (cached != null) {
            return cached;
        }
        boolean village = level != null && VillageCityTypeStorage.getVillageType(level, cityId) != null;
        VILLAGE_CITY_CACHE.put(cityId, village);
        return village;
    }

    public static boolean isLightNpc(CitizenData data, ServerLevel level) {
        return isVillageCityNpc(data, level);
    }

    public static boolean isLightNpcEntity(CitizenEntity entity, ServerLevel level) {
        if (entity == null) {
            return false;
        }
        if (isTouristEntity(entity) || isCaravanEntity(entity)) {
            return true;
        }
        if (level == null) {
            return false;
        }
        CitizenData data = CitizenManager.get(level).getCitizen(entity.getUUID()).orElse(null);
        return isVillageCityNpc(data, level);
    }

    public static boolean isStationary(CitizenEntity entity) {
        return isTouristEntity(entity) || isCaravanEntity(entity);
    }
}