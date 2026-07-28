package com.xy2407.nsukaddition.server.building;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 建造任务暂停协调器，每 tick 强制停止所有已暂停居民的建造运行时。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class BuildTaskPauseReconciler {

    private BuildTaskPauseReconciler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Set<UUID>> paused = BuildTaskTrackedState.getPausedByCity(level);
        if (paused.isEmpty()) {
            return;
        }
        for (Set<UUID> citizens : paused.values()) {
            for (UUID citizenId : citizens) {

                BuilderTaskControl.stopRuntime(level, citizenId);
            }
        }
    }
}
