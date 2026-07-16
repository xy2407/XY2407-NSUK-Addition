package com.xy2407.nsukaddition.server.material;

import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** 可用建材收集器，汇总玩家背包、城市仓库和建造箱附近的建材库存。 */
public final class AvailableMaterialCollector {

    private AvailableMaterialCollector() {
    }

    public static Map<String, Integer> collectAvailable(ServerLevel level, ServerPlayer player,
                                                        BuildingTaskData task, UUID cityId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (level == null || task == null) {
            return result;
        }

        Set<BlockPos> countedContainers = new HashSet<>();

        if (player != null) {
            countInventory(player.getInventory(), result);
        }

        if (cityId != null) {
            countCityWarehouses(level, cityId, result, countedContainers);
        }

        if (task.buildBoxPos() != null) {
            countAdjacentContainers(level, task.buildBoxPos(), result, countedContainers);
        }

        return result;
    }

    public static Map<String, Integer> countContainers(ServerLevel level, Collection<BlockPos> containerPositions) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (level == null || containerPositions == null) {
            return result;
        }
        Set<BlockPos> counted = new HashSet<>();
        for (BlockPos pos : containerPositions) {
            if (pos == null) continue;
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, pos);
            if (!counted.add(canonical)) continue;
            if (!GenericContainerAccess.isContainer(level, canonical)) continue;
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                countStack(snapshot.stack(), result);
            }
        }
        return result;
    }

    private static void countInventory(Inventory inventory, Map<String, Integer> result) {
        if (inventory == null) {
            return;
        }
        for (ItemStack stack : inventory.items) {
            countStack(stack, result);
        }
        for (ItemStack stack : inventory.armor) {
            countStack(stack, result);
        }
        for (ItemStack stack : inventory.offhand) {
            countStack(stack, result);
        }
    }

    private static void countCityWarehouses(ServerLevel level, UUID cityId,
                                            Map<String, Integer> result, Set<BlockPos> countedContainers) {
        try {
            Collection<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
            for (LogisticsWarehouseData warehouse : warehouses) {
                if (warehouse == null || warehouse.containers() == null) continue;
                for (BlockPos pos : warehouse.containers()) {
                    if (pos == null) continue;
                    BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, pos);
                    if (!countedContainers.add(canonical)) continue;
                    if (!GenericContainerAccess.isContainer(level, canonical)) continue;
                    for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                        countStack(snapshot.stack(), result);
                    }
                }
            }
        } catch (RuntimeException ignored) {

        }
    }

    private static void countAdjacentContainers(ServerLevel level, BlockPos buildBoxPos,
                                                Map<String, Integer> result, Set<BlockPos> countedContainers) {
        List<BlockPos> adjacent = WorkContainerService.adjacentContainers(level, buildBoxPos);
        for (BlockPos pos : adjacent) {
            if (pos == null) continue;
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, pos);
            if (!countedContainers.add(canonical)) continue;
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                countStack(snapshot.stack(), result);
            }
        }
    }

    private static void countStack(ItemStack stack, Map<String, Integer> result) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        String categoryKey = MaterialCategoryRegistry.getCategoryKey(id.toString());
        if (categoryKey == null) {
            return;
        }
        result.merge(categoryKey, stack.getCount(), Integer::sum);
    }

}
