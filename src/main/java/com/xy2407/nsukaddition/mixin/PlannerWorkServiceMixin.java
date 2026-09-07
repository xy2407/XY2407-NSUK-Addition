package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.planner.PlanningTaskData;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 修改 PlannerWorkService：规划完成后不再自动解雇 NPC 规划工人。 */
@Mixin(value = PlannerWorkService.class, remap = false)
public class PlannerWorkServiceMixin {

    @Redirect(
            method = "completeTask",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/job/CitizenEmploymentService;clearAfterJobFinished(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)Ljava/util/Optional;", remap = false),
            require = 0, allow = 1
    )
    private static Optional<CitizenData> nsuk$cancelAutoFire(ServerLevel level, UUID citizenId) {
        return Optional.empty();
    }

    /** 规划师取料/存放改接本城市物流仓库容器(距规划盒最近优先)，不再依赖规划盒相邻6面箱子；无仓库时回退原逻辑。 */
    @Inject(method = "resolveTaskChests", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$resolveWarehouseContainers(
            ServerLevel level, PlanningTaskData task, CallbackInfoReturnable<List<BlockPos>> cir) {
        if (level == null || task == null || task.cityId() == null) {
            return;
        }
        List<BlockPos> containers = new ArrayList<>();
        try {
            List<LogisticsWarehouseData> warehouses =
                    new ArrayList<>(LogisticsManager.get(level).warehouses(task.cityId()));
            warehouses.sort(Comparator.comparingDouble(wh -> wh.boxPos().distSqr(task.buildBoxPos())));
            for (LogisticsWarehouseData warehouse : warehouses) {
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
        if (!containers.isEmpty()) {
            cir.setReturnValue(containers.stream().distinct().toList());
        }
    }
}
