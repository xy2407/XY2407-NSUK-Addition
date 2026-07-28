package com.xy2407.nsukaddition.server.autorestock;

import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockService;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockSqliteStorage;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 自动补货服务端定时任务，周期性处理工业、商业、矿业与养殖控制箱的物品存取和补货。 */
public final class AutoRestockServerTick {
    private static final int STORE_INTERVAL = 600;
    private static int tickCounter;

    private AutoRestockServerTick() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overWorld = server.overworld();
        if (overWorld != null) {
            AutoRestockConfig.loadFromDatabase(overWorld);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        NsukWriteExecutor.shutdown();
        AutoRestockConfig.clear();
        AutoRestockSqliteStorage.clearServerCache(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < STORE_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            processStoreOnly(level);
        }
    }

    private static void processStoreOnly(ServerLevel level) {
        if (AutoRestockConfig.allEnabled().isEmpty()) return;

        for (var pos : AutoRestockConfig.allEnabled()) {
            if (!level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(common.cn.kafei.simukraft.registry.ModBlocks.INDUSTRIAL_CONTROL_BOX.get())) {
                AutoRestockService.storeIndustrialOutputs(level, pos);
            } else if (state.is(common.cn.kafei.simukraft.registry.ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
                AutoRestockService.processCommercialRestock(level, pos);
            } else if (state.is(ModBlocks.MINING_CONTROL_BOX.get())) {
                AutoRestockService.storeMiningOutputs(level, pos);
            } else if (state.is(ModBlocks.BREEDING_CONTROL_BOX.get())) {
                AutoRestockService.restockBreedingInputs(level, pos);
                AutoRestockService.storeBreedingOutputs(level, pos);
            } else if (state.is(common.cn.kafei.simukraft.registry.ModBlocks.NSUK_FARMLAND_BOX.get())) {
                AutoRestockService.restockFarmlandInputs(level, pos);
                AutoRestockService.storeFarmlandOutputs(level, pos);
            } else if (state.is(ModBlocks.RESTAURANT_CONTROL_BOX.get())) {
                AutoRestockService.restockRestaurantInputs(level, pos);
            } else {
                AutoRestockConfig.remove(level, pos);
            }
        }
    }
}
