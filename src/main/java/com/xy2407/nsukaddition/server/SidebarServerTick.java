package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 侧边栏数据服务端定时同步，每 tick 驱动 SidebarSyncService。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class SidebarServerTick {
    private SidebarServerTick() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level != null) SidebarSyncService.tick(level);
    }
}
