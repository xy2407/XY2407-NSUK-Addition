package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 批量写入调度器：每 20 tick 触发一次 WriteBatchBuffer 双缓冲切换与批量落库。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class WriteBatchBufferTick {

    private static long lastFlushTick = -1L;

    private WriteBatchBufferTick() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld() != null
                ? event.getServer().overworld().getGameTime() : 0L;
        if (lastFlushTick < 0L) {
            lastFlushTick = gameTime;
            return;
        }
        if (gameTime - lastFlushTick >= WriteBatchBuffer.FLUSH_INTERVAL_TICKS) {
            lastFlushTick = gameTime;
            WriteBatchBuffer.triggerFlush();
        }
    }
}
