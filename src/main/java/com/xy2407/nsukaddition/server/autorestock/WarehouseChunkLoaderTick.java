package com.xy2407.nsukaddition.server.autorestock;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 仓库箱 33 级常加载定时器：服务端低频周期对所有仓库箱维持常驻，不依赖自动补货。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class WarehouseChunkLoaderTick {

    /** 刷新间隔(tick)，10 秒一次，足够覆盖新建仓库且开销极低。 */
    private static final int REFRESH_INTERVAL = 200;

    private static long lastRefresh = -1L;

    private WarehouseChunkLoaderTick() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long current = server.getTickCount();
        if (lastRefresh < 0L || current - lastRefresh >= REFRESH_INTERVAL) {
            lastRefresh = current;
            for (ServerLevel level : server.getAllLevels()) {
                WarehouseChunkLoader.refreshAll(level);
            }
        }
    }
}