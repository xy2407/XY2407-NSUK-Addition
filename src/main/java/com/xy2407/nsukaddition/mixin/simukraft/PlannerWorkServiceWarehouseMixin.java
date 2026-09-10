package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.autorestock.WarehouseChunkLoader;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import common.cn.kafei.simukraft.planner.PlanningTaskData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 把规划师的材料来源与掉落目标从"建筑盒六面容器"改为该城市物流仓库的箱子，彻底不走六面容器。 */
@Mixin(value = PlannerWorkService.class, remap = false)
public class PlannerWorkServiceWarehouseMixin {

    @Inject(method = "resolveTaskChests", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$resolveTaskChestsFromWarehouse(ServerLevel level, PlanningTaskData task,
                                                            CallbackInfoReturnable<List<BlockPos>> cir) {
        if (level == null || task == null || task.buildBoxPos() == null) {
            return;
        }
        UUID cityId = LogisticsControlBoxService.cityIdFor(level, task.buildBoxPos());
        List<LogisticsWarehouseData> warehouses = cityId != null
                ? LogisticsManager.get(level).warehouses(cityId)
                : List.of();
        List<BlockPos> containers = new ArrayList<>();
        for (LogisticsWarehouseData warehouse : warehouses) {
            if (warehouse.containers() == null) {
                continue;
            }
            // 规划师用到这些仓库时也维持 33 级常加载，确保远处仓库箱可读/可取料
            WarehouseChunkLoader.forceLoad(level, warehouse);
            for (BlockPos candidate : warehouse.containers()) {
                if (candidate != null) {
                    containers.add(candidate.immutable());
                }
            }
        }
        // 一律返回仓库容器：该城市有物流仓库则从仓库箱取料/入库，无仓库则为空（规划师等材料建仓库）。
        cir.setReturnValue(List.copyOf(containers));
    }
}