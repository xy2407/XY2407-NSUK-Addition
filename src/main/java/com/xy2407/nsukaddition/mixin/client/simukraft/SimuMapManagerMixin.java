package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegion;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegionData;
import client.cn.kafei.simukraft.client.city.map.SimuMapStorage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SimuMapManager 性能优化：周期保存间隔 60s→180s、只保存脏 region、主线程采样预算 8→4，
 * 降低地图缓存磁盘写高峰与主线程 getBlockState 采样开销(消除"Can't keep up"超载)。
 */
@Mixin(value = SimuMapManager.class, remap = false)
public abstract class SimuMapManagerMixin {

    @Shadow
    private String currentWorldId;
    @Shadow
    private ResourceKey<Level> currentDimension;
    @Shadow
    private Map<Long, SimuMapRegion> regions;

    @ModifyConstant(method = "tick", constant = @Constant(longValue = 1200L), remap = false)
    private static long nsuk$slowerAutoSave(long original) {
        return 3600L;
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 8), remap = false)
    private int nsuk$lowerScanBudget(int original) {
        return 4;
    }

    @Inject(method = "autoSaveRegions", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$saveOnlyDirty(CallbackInfo ci) {
        if (currentWorldId == null || currentDimension == null || regions.isEmpty()) {
            ci.cancel();
            return;
        }
        List<SimuMapRegion> dirty = regions.values().stream()
                .filter(r -> {
                    SimuMapRegionData data = r.getData();
                    return data != null && data.isDirty();
                })
                .collect(Collectors.toList());
        if (!dirty.isEmpty()) {
            SimuMapStorage.saveAllAsync(currentWorldId, currentDimension, dirty, "periodic_cache", false);
        }
        ci.cancel();
    }
}
