package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.server.city.VillageTourismService;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** 服务器关闭时同步刷新待写入数据，防止内存队列中的变更丢失。 */
public final class ServerShutdownHandler {

    private ServerShutdownHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerStopping(ServerStoppingEvent event) {
        VillageTourismService.saveTouristIncome(event.getServer());
    }
}
