package com.xy2407.nsukaddition.server.autorestock;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockService;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockSqliteStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 自动补货服务端定时任务，周期性处理工业与商业控制箱的物品存取和补货。 */
public final class AutoRestockServerTick {
    private static final int STORE_INTERVAL = 200;
    private static int tickCounter;

    private AutoRestockServerTick() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        NsukAddition.LOGGER.info("[AutoRestockServerTick] onServerStarting");
        MinecraftServer server = event.getServer();
        ServerLevel overWorld = server.getLevel(ServerLevel.OVERWORLD);
        if (overWorld != null) {
            AutoRestockConfig.loadFromDatabase(overWorld);
            NsukAddition.LOGGER.info("[AutoRestockServerTick] loaded {} positions from DB", AutoRestockConfig.allEnabled().size());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AutoRestockConfig.clear();
        AutoRestockSqliteStorage.clearServerCache(event.getServer());
        NsukAddition.LOGGER.info("[AutoRestockServerTick] onServerStopping, cache cleared");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < STORE_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) return;
        NsukAddition.LOGGER.info("[AutoRestockServerTick] 200-tick fired, enabled positions = {}",
                AutoRestockConfig.allEnabled().size());
        for (ServerLevel level : server.getAllLevels()) {
            processStoreOnly(level);
        }
    }

    private static void processStoreOnly(ServerLevel level) {
        if (AutoRestockConfig.allEnabled().isEmpty()) return;

        for (var pos : AutoRestockConfig.allEnabled()) {
            if (!level.isLoaded(pos)) continue;
            if (isIndustrialBox(level, pos)) {
                NsukAddition.LOGGER.info("[AutoRestockServerTick] processing industrial box at {}", pos);
                AutoRestockService.storeIndustrialOutputs(level, pos);
            } else if (isCommercialBox(level, pos)) {
                NsukAddition.LOGGER.info("[AutoRestockServerTick] processing commercial box at {}", pos);
                AutoRestockService.processCommercialRestock(level, pos);
            } else {
                NsukAddition.LOGGER.warn("[AutoRestockServerTick] pos {} is neither industrial nor commercial!", pos);
            }
        }
    }

    private static boolean isIndustrialBox(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).is(
                common.cn.kafei.simukraft.registry.ModBlocks.INDUSTRIAL_CONTROL_BOX.get());
    }

    private static boolean isCommercialBox(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).is(
                common.cn.kafei.simukraft.registry.ModBlocks.COMMERCIAL_CONTROL_BOX.get());
    }
}
