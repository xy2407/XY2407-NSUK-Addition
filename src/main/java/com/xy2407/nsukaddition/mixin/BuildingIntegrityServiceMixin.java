package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** 修复BuildingIntegrityService.tick一次性扫描全部建筑所有方块造成卡顿：改为每tick仅扫描N个建筑，轮流分帧覆盖。 */
@Mixin(BuildingIntegrityService.class)
public class BuildingIntegrityServiceMixin {

    @Unique
    private static final int NSUK$SCAN_BUDGET_PER_TICK = 10;

    @Unique
    private static int nsuk$scanCursor = 0;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void nsuk$tickFrameSliced(ServerLevel level, CallbackInfo ci) {
        if (level == null || level.isClientSide()) {
            ci.cancel();
            return;
        }
        int threshold = ServerConfig.buildingIntegrityAutoDemolishThresholdPercent();
        if (threshold <= 0) {
            ci.cancel();
            return;
        }
        int interval = Math.max(20, ServerConfig.buildingIntegrityCheckIntervalTicks());
        if (level.getGameTime() % interval != 0L) {
            ci.cancel();
            return;
        }
        List<PlacedBuildingRecord> buildings = new ArrayList<>(PlacedBuildingService.getBuildings(level));
        if (buildings.isEmpty()) {
            ci.cancel();
            return;
        }
        if (nsuk$scanCursor >= buildings.size()) {
            nsuk$scanCursor = 0;
        }
        int processed = 0;
        int index = nsuk$scanCursor;
        while (processed < NSUK$SCAN_BUDGET_PER_TICK && processed < buildings.size()) {
            PlacedBuildingRecord building = buildings.get(index);
            if (building != null) {
                BuildingIntegrityService.IntegritySnapshot snapshot = BuildingIntegrityService.snapshot(level, building);
                if (snapshot.available() && snapshot.totalBlocks() > 0) {
                    double percent = snapshot.percent();
                    if (percent < threshold) {
                        nsuk$autoDemolish(level, building, percent, threshold);
                    }
                }
            }
            index = (index + 1) % buildings.size();
            processed++;
        }
        nsuk$scanCursor = index;
        ci.cancel();
    }

    @Unique
    private static void nsuk$autoDemolish(ServerLevel level, PlacedBuildingRecord building, double percent, int threshold) {
        if (common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService.demolish(level, building)) {
            common.cn.kafei.simukraft.SimuKraft.LOGGER.info("Simukraft: Auto demolished building {} because integrity {:.1f}% is below {}%",
                    building.displayName(), percent, threshold);
        }
    }
}
