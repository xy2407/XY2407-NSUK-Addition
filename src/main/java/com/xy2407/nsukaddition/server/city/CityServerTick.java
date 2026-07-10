package com.xy2407.nsukaddition.server.city;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import com.xy2407.nsukaddition.common.city.CityLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** 城市服务端定时任务，驱动城市等级事件、旅游和移民服务。 */
public final class CityServerTick {
    private CityServerTick() {}

    private static long lastDispatchedDay = -1;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) return;

        long day = overworld.getDayTime() / 24000L;
        if (day != lastDispatchedDay) {
            lastDispatchedDay = day;
            for (CityData city : CityService.allCities(overworld)) {
                if (city == null) continue;
                UUID cityId = city.cityId();
                CityLevel level = CityLevel.fromLevel(city.cityLevel());

                if (level.atLeast(CityLevel.VILLAGE)) {
                    VillageTourismService.beginDay(overworld, cityId, day);
                }
                if (level.atLeast(CityLevel.TOWN)) {
                    TownImmigrationService.trySpawnImmigrant(overworld, cityId, day);
                }
            }
        }

        VillageTourismService.onServerTick(overworld);
        TownImmigrationService.onServerTick(overworld);
    }
}
