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
    private static final int STORE_INTERVAL = 200;
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
            if (isIndustrialBox(level, pos)) {
                AutoRestockService.storeIndustrialOutputs(level, pos);
            } else if (isCommercialBox(level, pos)) {
                AutoRestockService.processCommercialRestock(level, pos);
            } else if (isMiningBox(level, pos)) {
                AutoRestockService.storeMiningOutputs(level, pos);
            } else if (isBreedingBox(level, pos)) {
                AutoRestockService.restockBreedingInputs(level, pos);
                AutoRestockService.storeBreedingOutputs(level, pos);
            } else if (isFarmlandBox(level, pos)) {
                AutoRestockService.restockFarmlandInputs(level, pos);
                AutoRestockService.storeFarmlandOutputs(level, pos);
            } else {
                AutoRestockConfig.remove(level, pos);
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

    private static boolean isMiningBox(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.MINING_CONTROL_BOX.get());
    }

    private static boolean isBreedingBox(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.BREEDING_CONTROL_BOX.get());
    }

    private static boolean isFarmlandBox(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).is(common.cn.kafei.simukraft.registry.ModBlocks.NSUK_FARMLAND_BOX.get());
    }
}
