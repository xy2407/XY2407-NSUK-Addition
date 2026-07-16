package com.xy2407.nsukaddition.server.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.mining.MiningBoxSqliteStorage;
import com.xy2407.nsukaddition.common.mining.MiningWorkService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 挖矿服务端定时任务，驱动挖矿工作逻辑并在服务器停止时清理缓存。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class MiningServerTick {

    private MiningServerTick() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level != null) {
            MiningWorkService.tick(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        MiningBoxSqliteStorage.clearServerCache(server);
    }
}
