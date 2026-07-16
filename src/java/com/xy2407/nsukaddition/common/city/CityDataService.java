package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** 城市数据的查询与等级设置服务，提供按标识查找城市及变更等级的方法。 */
public final class CityDataService {

    private CityDataService() {}

    public static CityData getCity(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return null;
        }
        return CityService.findCity(level, cityId).orElse(null);
    }

    public static boolean setCityLevel(ServerLevel level, UUID cityId, int newLevel) {
        CityData city = getCity(level, cityId);
        if (city == null) {
            return false;
        }

        CityLevelAccessor.setCityLevel(city, newLevel);

        CityManager.get(level).setDirty();
        return true;
    }
}
