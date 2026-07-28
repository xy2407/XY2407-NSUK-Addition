package com.xy2407.nsukaddition.server.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.breeding.BreedingBoxSqliteStorage;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxViewSyncService;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import com.xy2407.nsukaddition.common.breeding.BreedingWorkService;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import com.xy2407.nsukaddition.server.SidebarDataCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 养殖服务端定时任务，驱动养殖工作逻辑并在服务器停止时清理缓存。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class BreedingServerTick {

    private BreedingServerTick() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level != null) {
            BreedingWorkService.tick(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        NsukWriteExecutor.shutdown();
        SidebarDataCache.shutdown();
        MinecraftServer server = event.getServer();
        BreedingControlBoxViewSyncService.clearServerCaches(server);
        BreedingDefinitionLoader.clearCache();
        BreedingBoxSqliteStorage.clearServerCache(server);
    }
}
