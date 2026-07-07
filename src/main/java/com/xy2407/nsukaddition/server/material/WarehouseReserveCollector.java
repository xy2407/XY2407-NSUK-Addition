package com.xy2407.nsukaddition.server.material;

import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** 仓库储备收集器，收集指定城市的物流仓库中所有容器的建材储量。 */
public final class WarehouseReserveCollector {

    private WarehouseReserveCollector() {
    }

    public static Map<String, Integer> collectReserve(ServerLevel level, UUID cityId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (level == null || cityId == null) {
            return result;
        }

        List<BlockPos> containerPositions = new ArrayList<>();
        try {
            Collection<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
            for (LogisticsWarehouseData warehouse : warehouses) {
                if (warehouse == null || warehouse.containers() == null) {
                    continue;
                }
                containerPositions.addAll(warehouse.containers());
            }
        } catch (RuntimeException ignored) {

        }

        return AvailableMaterialCollector.countContainers(level, containerPositions);
    }
}
