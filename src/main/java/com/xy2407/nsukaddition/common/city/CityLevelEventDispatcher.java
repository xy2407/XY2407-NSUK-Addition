package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** 每日按城市等级分发旅游和移民事件，在服务端滴答中检测新一天并触发逻辑。 */
public final class CityLevelEventDispatcher {

    private CityLevelEventDispatcher() {}

    private static long lastDispatchedDay = -1;

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long day = overworld.getDayTime() / 24000L;
        if (day == lastDispatchedDay) return;
        lastDispatchedDay = day;

        for (CityData city : CityService.allCities(overworld)) {
            if (city == null) continue;
            UUID cityId = city.cityId();
            CityLevel level = CityLevel.fromLevel(city.cityLevel());

            if (level.atLeast(CityLevel.VILLAGE)) {
                com.xy2407.nsukaddition.server.city.VillageTourismService.beginDay(overworld, cityId, day);
            }
            if (level.atLeast(CityLevel.TOWN)) {
                com.xy2407.nsukaddition.server.city.TownImmigrationService.trySpawnImmigrant(overworld, cityId, day);
            }
        }
    }
}
