package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.WorkMaterialCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 建筑工人取料改为直接从城市物流仓库容器消耗，替代原临侧箱子取料。 */
@Mixin(WorkMaterialCache.class)
public abstract class WorkMaterialCacheWarehouseMixin {

    @Shadow
    @Final
    private BlockPos workBlockPos;

    @Inject(method = "discoverAdjacentContainers", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$discoverWarehouseContainers(ServerLevel level, CallbackInfoReturnable<List<BlockPos>> cir) {
        if (level == null || this.workBlockPos == null) {
            return;
        }
        UUID cityId = LogisticsControlBoxService.cityIdFor(level, this.workBlockPos);
        if (cityId == null) {
            return;
        }

        List<BlockPos> containers = new ArrayList<>();
        try {
            for (LogisticsWarehouseData warehouse : LogisticsManager.get(level).warehouses(cityId)) {
                if (warehouse == null || warehouse.containers() == null) {
                    continue;
                }
                for (BlockPos raw : warehouse.containers()) {
                    if (raw == null || !level.isLoaded(raw)) {
                        continue;
                    }
                    containers.add(GenericContainerAccess.canonicalContainerPos(level, raw));
                }
            }
        } catch (RuntimeException ignored) {
            return;
        }
        cir.setReturnValue(containers.stream().distinct().toList());
    }
}