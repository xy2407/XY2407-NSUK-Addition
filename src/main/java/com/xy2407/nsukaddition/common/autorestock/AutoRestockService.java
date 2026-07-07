package com.xy2407.nsukaddition.common.autorestock;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.commercial.CommercialBoxData;
import common.cn.kafei.simukraft.commercial.CommercialBoxManager;
import common.cn.kafei.simukraft.commercial.CommercialDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import common.cn.kafei.simukraft.commercial.CommercialOffer;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialInputRequirements;
import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 自动补货服务：工业产出存入仓库、从仓库为工业 input 容器补货、从仓库为商业补货。 */

public final class AutoRestockService {

    private static final int COMMERCIAL_MATERIAL_RADIUS_XZ = 5;
    private static final int COMMERCIAL_MATERIAL_RADIUS_Y = 2;

    private AutoRestockService() {}

    public static void storeIndustrialOutputs(ServerLevel level, BlockPos pos) {
        NsukAddition.LOGGER.info("[AutoRestockService] storeIndustrialOutputs called for pos={}", pos);
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        IndustrialBoxData data = manager.get(pos);
        if (data == null) {
            NsukAddition.LOGGER.warn("[AutoRestockService] data is null at {}, removing from config", pos);
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) {
            NsukAddition.LOGGER.info("[AutoRestockService] data.running() = false, skip");
            return;
        }

        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            NsukAddition.LOGGER.warn("[AutoRestockService] resolveBuilding returned null for {}", pos);
            return;
        }

        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) {
            NsukAddition.LOGGER.warn("[AutoRestockService] loadResult invalid for building at {}", building.worldOrigin());
            return;
        }
        IndustrialDefinition definition = loadResult.definition();
        if (definition == null) return;

        List<BlockPos> outputContainers = IndustrialControlBoxService.resolveContainerPositions(
                building, definition, "output");
        if (outputContainers.isEmpty()) {
            NsukAddition.LOGGER.warn("[AutoRestockService] no output containers found");
            return;
        }
        NsukAddition.LOGGER.info("[AutoRestockService] outputContainers = {}", outputContainers);

        BlockPos boxPos = building.worldOrigin();
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, boxPos);
        if (warehouse == null) {
            NsukAddition.LOGGER.warn("[AutoRestockService] no warehouse found near {}", boxPos);
            return;
        }

        BlockPos warehousePos = warehouse.boxPos();
        for (BlockPos container : outputContainers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().isEmpty()) continue;
                ItemStack stack = slot.stack();
                NsukAddition.LOGGER.info("[AutoRestockService] storing {} from {} to warehouse", stack, container);
                ItemStack remaining = LogisticsWarehouseInventoryService.insert(level, warehousePos, stack.copy());
                int deposited = stack.getCount() - remaining.getCount();
                if (deposited > 0) {
                    GenericContainerAccess.extractFromSlot(level, container,
                            slot.slot(), slot.access(), slot.side(), deposited,
                            s -> ItemStack.isSameItemSameComponents(s, stack));
                    NsukAddition.LOGGER.info("[AutoRestockService] deposited {}, remaining {}", deposited, remaining);
                }
            }
        }
    }

    public static void restockIndustrialInputs(ServerLevel level, BlockPos pos) {
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        IndustrialBoxData data = manager.get(pos);
        if (data == null) {
            NsukAddition.LOGGER.warn("[AutoRestockService] industrial data null at {}, removing from config", pos);
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) {
            NsukAddition.LOGGER.info("[AutoRestockService] industrial box not running at {}, skip input restock", pos);
            return;
        }

        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            NsukAddition.LOGGER.warn("[AutoRestockService] no industrial building found for {}", pos);
            return;
        }

        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) {
            NsukAddition.LOGGER.warn("[AutoRestockService] industrial definition invalid for building at {}", building.worldOrigin());
            return;
        }
        IndustrialDefinition definition = loadResult.definition();

        IndustrialDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null || recipe.inputs().isEmpty()) {
            NsukAddition.LOGGER.info("[AutoRestockService] no recipe or no inputs for industrial box at {}", pos);
            return;
        }

        List<BlockPos> inputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, "input");
        if (inputContainers.isEmpty()) {
            NsukAddition.LOGGER.info("[AutoRestockService] no input containers for industrial box at {}", pos);
            return;
        }

        BlockPos boxPos = building.worldOrigin();
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, boxPos);
        if (warehouse == null) {
            NsukAddition.LOGGER.info("[AutoRestockService] no warehouse found near {}", boxPos);
            return;
        }

        List<IndustrialDefinition.ItemRequirement> flatInputs =
                IndustrialInputRequirements.flattenItems(recipe.inputs());

        for (IndustrialDefinition.ItemRequirement input : flatInputs) {
            if (!input.consume()) continue;
            IndustrialItemStackSpec spec = input.spec();
            if (spec.isEmpty()) continue;
            int required = input.count();

            int existing = countMatchingInContainers(level, inputContainers, spec);
            int shortage = required - existing;
            if (shortage <= 0) continue;

            ItemStack extracted = extractSpecFromWarehouse(level, warehouse, spec, shortage);
            if (extracted.isEmpty()) continue;

            ItemStack leftover = insertIntoContainers(level, inputContainers, extracted);
            int deposited = extracted.getCount() - leftover.getCount();
            if (deposited > 0) {
                NsukAddition.LOGGER.info("[AutoRestockService] restocked {} x{} to industrial input at {}",
                        spec.displayItemId(), deposited, pos);
            }
            if (!leftover.isEmpty()) {
                LogisticsWarehouseInventoryService.insert(level, warehouse.boxPos(), leftover);
            }
        }
    }

    private static int countMatchingInContainers(ServerLevel level, List<BlockPos> containers,
                                                  IndustrialItemStackSpec spec) {
        int count = 0;
        for (BlockPos container : containers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (spec.matches(slot.stack(), level.registryAccess())) {
                    count += slot.stack().getCount();
                }
            }
        }
        return count;
    }

    private static ItemStack extractSpecFromWarehouse(ServerLevel level, LogisticsWarehouseData warehouse,
                                                       IndustrialItemStackSpec spec, int count) {
        ItemStack result = ItemStack.EMPTY;
        int remaining = count;

        Set<BlockPos> visited = new LinkedHashSet<>();
        for (BlockPos rawContainer : warehouse.containers()) {
            if (remaining <= 0) break;
            if (!level.isLoaded(rawContainer)) continue;
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, rawContainer);
            if (!visited.add(canonical.immutable())) continue;

            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                if (remaining <= 0) break;
                if (!spec.matches(slot.stack(), level.registryAccess())) continue;

                int amount = Math.min(remaining, slot.stack().getCount());
                ItemStack extracted = GenericContainerAccess.extractFromSlot(level, canonical,
                        slot.slot(), slot.access(), slot.side(), amount,
                        s -> spec.matches(s, level.registryAccess()));
                if (extracted.isEmpty()) continue;

                if (result.isEmpty()) {
                    result = extracted.copy();
                } else {
                    result.grow(extracted.getCount());
                }
                remaining -= extracted.getCount();
            }
        }
        return result;
    }

    public static void processCommercialRestock(ServerLevel level, BlockPos pos) {
        CommercialBoxManager manager = CommercialBoxManager.get(level);
        CommercialBoxData data = manager.get(pos);
        if (data == null) {
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) return;

        var building = common.cn.kafei.simukraft.building.PlacedBuildingService
                .findByContainedPosAndCategory(level, pos, "commercial", "commerce");
        if (building == null) return;

        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) return;
        CommercialDefinition definition = loadResult.definition();
        if (definition == null) return;

        java.util.ArrayList<ItemStack> needed = new java.util.ArrayList<>();
        for (CommercialOffer offer : definition.offers()) {
            if (offer.stock() == null) continue;
            for (CommercialOffer.MaterialRequirement mat : offer.stock().materials()) {
                Item item = mat.item();
                if (item == Items.AIR) continue;
                needed.add(new ItemStack(item, mat.count()));
            }
        }
        if (needed.isEmpty()) return;

        List<BlockPos> inputContainers = resolveCommercialContainerPositions(building, definition, "input");
        if (inputContainers.isEmpty()) return;

        List<ItemStack> existingInputs = collectContainerItems(level, inputContainers);
        BlockPos boxPos = building.worldOrigin();

        for (ItemStack need : needed) {
            int existing = 0;
            for (ItemStack e : existingInputs) {
                if (ItemStack.isSameItemSameComponents(e, need)) existing += e.getCount();
            }
            int shortage = need.getCount() - existing;
            if (shortage <= 0) continue;

            ItemStack extracted = extractFromNearestWarehouse(level, boxPos, need, shortage);
            if (!extracted.isEmpty()) {
                insertIntoContainers(level, inputContainers, extracted);
            }
        }
    }

    private static ItemStack extractFromNearestWarehouse(ServerLevel level, BlockPos pos,
                                                         ItemStack template, int count) {
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, pos);
        if (warehouse == null) return ItemStack.EMPTY;
        return LogisticsWarehouseInventoryService.extract(level, warehouse.boxPos(), template, count);
    }

    private static LogisticsWarehouseData findNearestWarehouse(ServerLevel level, BlockPos pos) {
        return LogisticsManager.get(level).warehouses().stream()
                .min(Comparator.comparingDouble(w -> w.boxPos().distSqr(pos)))
                .orElse(null);
    }

    private static List<ItemStack> collectContainerItems(ServerLevel level, List<BlockPos> containers) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        for (BlockPos container : containers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!slot.stack().isEmpty()) stacks.add(slot.stack());
            }
        }
        return stacks;
    }

    private static ItemStack insertIntoContainers(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (BlockPos container : containers) {
            if (remaining.isEmpty()) break;
            if (!level.isLoaded(container)) continue;
            remaining = GenericContainerAccess.insert(level, container, remaining);
        }
        return remaining;
    }

    private static List<BlockPos> resolveCommercialContainerPositions(
            PlacedBuildingRecord building, CommercialDefinition definition, String containerId) {
        if (definition == null || containerId == null || containerId.isBlank()) return List.of();
        CommercialDefinition.ContainerDefinition container = definition.containers().get(containerId);
        if (container == null || !"structure_pos".equalsIgnoreCase(container.type())) return List.of();
        return IndustrialCoordinateResolver.resolvePositions(building, container.positions());
    }
}
