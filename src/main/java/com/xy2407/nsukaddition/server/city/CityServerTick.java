package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.common.city.CityLevelEventDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 城市服务端定时任务，驱动城市等级事件、旅游和移民服务。 */
public final class CityServerTick {
    private CityServerTick() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) return;

        CityLevelEventDispatcher.onServerTick(event.getServer());

        VillageTourismService.onServerTick(overworld);
        TownImmigrationService.onServerTick(overworld);
    }
}
