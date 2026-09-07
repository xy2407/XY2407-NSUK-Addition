package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 规划材料扫描改为读取本城市物流仓库容器(距规划盒最近优先)，不再只读规划盒相邻6面箱子。
 * 注意 simukraft 的 PlannerNetworkValidation.adjacentContainers 返回 java.util.Set，
 * redirect 的 target 描述符必须写 Ljava/util/Set;，否则运行时静默失效导致界面报"无容器"。
 */
@Mixin(value = common.cn.kafei.simukraft.network.planner.PlannerMaterialScanRequestPacket.class, remap = false)
public abstract class PlannerMaterialScanRequestPacketMixin {

    @Redirect(
            method = "handle",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/network/planner/PlannerNetworkValidation;adjacentContainers(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Ljava/util/Set;",
                    remap = false),
            require = 0, allow = 1
    )
    private static Set<BlockPos> nsuk$scanFromWarehouse(ServerLevel level, BlockPos boxPos) {
        UUID cityId = LogisticsControlBoxService.cityIdFor(level, boxPos);
        if (cityId == null) {
            return nsukFallbackAdjacent(level, boxPos);
        }
        Set<BlockPos> containers = new LinkedHashSet<>();
        try {
            List<LogisticsWarehouseData> warehouses = new ArrayList<>(LogisticsManager.get(level).warehouses(cityId));
            warehouses.sort(Comparator.comparingDouble(wh -> wh.boxPos().distSqr(boxPos)));
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
            return nsukFallbackAdjacent(level, boxPos);
        }
        return containers.isEmpty() ? nsukFallbackAdjacent(level, boxPos) : containers;
    }

    /** nsukFallbackAdjacent: 无仓库/读取异常时回退相邻6面容器(原版逻辑)。 */
    private static Set<BlockPos> nsukFallbackAdjacent(ServerLevel level, BlockPos boxPos) {
        Set<BlockPos> list = new LinkedHashSet<>();
        for (Direction d : Direction.values()) {
            BlockPos c = boxPos.relative(d);
            if (level != null && level.isLoaded(c) && GenericContainerAccess.isContainer(level, c)) {
                list.add(GenericContainerAccess.canonicalContainerPos(level, c));
            }
        }
        return list;
    }
}