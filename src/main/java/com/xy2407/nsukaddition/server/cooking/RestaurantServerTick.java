package com.xy2407.nsukaddition.server.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.cooking.CookingWorkService;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxSqliteStorage;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxViewSyncService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.cooking.RestaurantDiningService;
import com.xy2407.nsukaddition.common.entity.SitEntity;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;

/** 烹饪服务端定时任务，驱动烹饪与就餐逻辑并在服务器停止时清理缓存。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class RestaurantServerTick {

    private static final String DINING_STATUS_PREFIX = "gui.xy2407_nsuk_addition.cooking.dining.";

    private RestaurantServerTick() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level != null) {
            CookingWorkService.tick(level);
            RestaurantDiningService.tick(level);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;
        RestaurantBoxSqliteStorage.clearAllOccupiedSeats(level);
        var staleSeats = new ArrayList<SitEntity>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SitEntity sit) staleSeats.add(sit);
        }
        for (SitEntity sit : staleSeats) {
            for (Entity passenger : sit.getPassengers()) {
                resetDiningStatus(level, passenger);
            }
            sit.ejectPassengers();
            sit.discard();
        }
        if (!staleSeats.isEmpty()) {
            NsukAddition.LOGGER.info("清理残留 SitEntity: {} 个", staleSeats.size());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (level != null) {
            RestaurantDiningService.cleanupAllDiners(level);
        }
        NsukWriteExecutor.shutdown();
        MinecraftServer server = event.getServer();
        RestaurantControlBoxViewSyncService.clearServerCaches(server);
        RestaurantDefinitionLoader.clearCache();
    }

    private static void resetDiningStatus(ServerLevel level, Entity entity) {
        if (!(entity instanceof CitizenEntity citizen)) return;
        var data = CitizenService.findCitizen(level, citizen.getUUID()).orElse(null);
        if (data != null && data.statusLabel() != null && data.statusLabel().startsWith(DINING_STATUS_PREFIX)) {
            data.setStatusLabel("");
            data.setWorkNeedDetail("");
            CitizenService.save(level, citizen.getUUID());
            CitizenManager.get(level).syncEntity(citizen);
        }
    }
}
