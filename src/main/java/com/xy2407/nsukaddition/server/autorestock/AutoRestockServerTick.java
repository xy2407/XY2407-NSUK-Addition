package com.xy2407.nsukaddition.server.autorestock;

import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockService;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockSqliteStorage;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxData;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ConcurrentHashMap;

/** 自动补货服务端定时任务，周期性处理工业、商业、矿业与养殖控制箱的物品存取和补货。 */
public final class AutoRestockServerTick {
    private static final int STORE_INTERVAL = 600;
    private static int tickCounter;

    /** 已预热的补货盒：首轮只强加载仓库，等待区块异步加载完成；次轮起才真正读写，避免首轮空窗。 */
    private static final ConcurrentHashMap.KeySetView<BlockPos, Boolean> PRIMED =
            ConcurrentHashMap.newKeySet();

    private AutoRestockServerTick() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overWorld = server.overworld();
        if (overWorld != null) {
            AutoRestockConfig.loadFromDatabase(overWorld);
            VillageCityTypeStorage.preloadAll(overWorld);
        }
        FreeMarketRepository.preloadAll();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WarehouseChunkLoader.releaseAll(event.getServer());
        PRIMED.clear();
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
        var enabled = AutoRestockConfig.allEnabled();
        if (enabled.isEmpty()) {
            return;
        }

        for (var pos : AutoRestockConfig.allEnabled()) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            // 先确保该盒所用到的物流仓库箱子区块保持加载，避免远处仓库读不到/丢料。
            AutoRestockService.keepWarehousesLoaded(level, pos);
            // 首轮只强加载(等待区块异步加载)，次轮起才真正读写。
            if (PRIMED.add(pos.immutable())) {
                continue;
            }
            var state = level.getBlockState(pos);
            if (state.is(common.cn.kafei.simukraft.registry.ModBlocks.INDUSTRIAL_CONTROL_BOX.get())) {
                AutoRestockService.storeIndustrialOutputs(level, pos);
            } else if (state.is(common.cn.kafei.simukraft.registry.ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
                AutoRestockService.processCommercialRestock(level, pos);
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

        // 钻井控制盒：仅处理已手动开启自动补货的盒(左上角开关)，产出入库 + 缺工具补钻杆/钻头。
        for (MineralDrillingBoxData drill : MineralDrillingBoxManager.get(level).all()) {
            BlockPos drillPos = drill.boxPos();
            if (drillPos == null || !level.isLoaded(drillPos)) {
                continue;
            }
            if (!AutoRestockConfig.isEnabled(drillPos)) {
                continue;
            }
            AutoRestockService.keepWarehousesLoaded(level, drillPos);
            // 首轮只强加载(等待区块异步加载)，次轮起才真正读写。
            if (PRIMED.add(drillPos.immutable())) {
                continue;
            }
            AutoRestockService.storeMineralOutputs(level, drillPos);
            AutoRestockService.restockMineralTools(level, drillPos);
        }
    }
}
