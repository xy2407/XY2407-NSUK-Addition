package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.server.level.ServerLevel;

/**
 * 城市激活判定工具:核心区块已加载即视为激活,供每日运算按需跳过未激活城市。
 */
public final class SimuKraftCityActivation {
    private SimuKraftCityActivation() {
    }

    public static boolean hasActiveCity(ServerLevel level) {
        if (level == null) {
            return false;
        }
        for (CityData city : CityService.allCities(level)) {
            if (city != null && level.isLoaded(city.cityCorePos())) {
                return true;
            }
        }
        return false;
    }
}
