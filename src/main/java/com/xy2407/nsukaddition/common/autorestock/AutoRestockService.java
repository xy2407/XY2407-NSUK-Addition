package com.xy2407.nsukaddition.common.autorestock;

import com.xy2407.nsukaddition.common.block.entity.MiningControlBoxBlockEntity;
import com.xy2407.nsukaddition.common.breeding.BreedingBoxData;
import com.xy2407.nsukaddition.common.breeding.BreedingBoxManager;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinition;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import com.xy2407.nsukaddition.common.mining.MiningBoxData;
import com.xy2407.nsukaddition.common.mining.MiningBoxManager;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxService;
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
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.entity.BlockEntity;

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
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        IndustrialBoxData data = manager.get(pos);
        if (data == null) {
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) return;

        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) return;

        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) return;
        IndustrialDefinition definition = loadResult.definition();
        if (definition == null) return;

        List<BlockPos> outputContainers = IndustrialControlBoxService.resolveContainerPositions(
                building, definition, "output");
        if (outputContainers.isEmpty()) return;

        BlockPos boxPos = building.worldOrigin();
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, boxPos);
        if (warehouse == null) return;

        BlockPos warehousePos = warehouse.boxPos();
        for (BlockPos container : outputContainers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().isEmpty()) continue;
                ItemStack stack = slot.stack();
                ItemStack remaining = LogisticsWarehouseInventoryService.insert(level, warehousePos, stack.copy());
                int deposited = stack.getCount() - remaining.getCount();
                if (deposited > 0) {
                    GenericContainerAccess.extractFromSlot(level, container,
                            slot.slot(), slot.access(), slot.side(), deposited,
                            s -> ItemStack.isSameItemSameComponents(s, stack));
                }
            }
        }
    }

    public static void restockIndustrialInputs(ServerLevel level, BlockPos pos) {
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        IndustrialBoxData data = manager.get(pos);
        if (data == null) {
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) return;

        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) return;

        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) return;
        IndustrialDefinition definition = loadResult.definition();

        IndustrialDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null || recipe.inputs().isEmpty()) return;

        List<BlockPos> inputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, "input");
        if (inputContainers.isEmpty()) return;

        BlockPos boxPos = building.worldOrigin();
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, boxPos);
        if (warehouse == null) return;

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

    /** 矿工盒子入库：从仓库补充镐子，并将自动绑定箱子中的物品存入仓库。 */
    public static void storeMiningOutputs(ServerLevel level, BlockPos pos) {
        if (!MiningControlBoxService.isBoxBlockValid(level, pos)) return;
        MiningBoxData data = MiningBoxManager.get(level).get(pos);
        if (data == null || !data.running()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MiningControlBoxBlockEntity box)) return;

        BlockPos warehousePos = nearestWarehousePos(level, pos);
        if (warehousePos == null) return;

        for (int slot = 0; slot < MiningControlBoxBlockEntity.PICKAXE_SLOTS; slot++) {
            ItemStack slotStack = box.pickaxes().getStackInSlot(slot);
            if (!slotStack.isEmpty()) continue;

            ItemStack pickaxe = extractPickaxeFromWarehouse(level, warehousePos);
            if (pickaxe.isEmpty()) break;

            box.pickaxes().setStackInSlot(slot, pickaxe);
            box.setChanged();
        }

        BlockPos chestPos = MiningControlBoxService.resolveContainerPos(level, pos);
        if (chestPos == null || !level.isLoaded(chestPos)) return;

        for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, chestPos)) {
            if (slot.stack().isEmpty()) continue;
            ItemStack stack = slot.stack();
            ItemStack remaining = LogisticsWarehouseInventoryService.insert(level, warehousePos, stack.copy());
            int deposited = stack.getCount() - remaining.getCount();
            if (deposited > 0) {
                GenericContainerAccess.extractFromSlot(level, chestPos,
                        slot.slot(), slot.access(), slot.side(), deposited,
                        s -> ItemStack.isSameItemSameComponents(s, stack));
            }
        }
    }

    private static ItemStack extractPickaxeFromWarehouse(ServerLevel level, BlockPos warehousePos) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouses().stream()
                .filter(w -> w.boxPos().equals(warehousePos))
                .findFirst().orElse(null);
        if (warehouse == null) return ItemStack.EMPTY;

        Set<BlockPos> visited = new LinkedHashSet<>();
        for (BlockPos rawContainer : warehouse.containers()) {
            if (!level.isLoaded(rawContainer)) continue;
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, rawContainer);
            if (!visited.add(canonical.immutable())) continue;

            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                if (slot.stack().isEmpty()) continue;
                if (!(slot.stack().getItem() instanceof PickaxeItem)) continue;
                if (slot.stack().getDamageValue() >= slot.stack().getMaxDamage()) continue;

                return GenericContainerAccess.extractFromSlot(level, canonical,
                        slot.slot(), slot.access(), slot.side(), 1,
                        s -> s.getItem() instanceof PickaxeItem && s.getDamageValue() < s.getMaxDamage());
            }
        }
        return ItemStack.EMPTY;
    }

    public static void restockBreedingInputs(ServerLevel level, BlockPos pos) {
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.get(pos);
        if (data == null || !data.running()) return;

        var building = BreedingControlBoxService.resolveBuilding(level, pos);
        if (building == null) return;

        BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) return;

        BreedingDefinition definition = loadResult.definition();
        BreedingDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null) return;

        String feedItemId = recipe.effectiveFeedItem();
        if (feedItemId.isBlank()) return;

        List<BlockPos> inputContainers = resolveBreedingContainerPositions(building, definition, "input", pos);
        if (inputContainers.isEmpty()) return;

        BlockPos warehousePos = nearestWarehousePos(level, building.worldOrigin());
        if (warehousePos == null) return;

        int required = 16;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.tryParse(feedItemId));
        if (item == null || item == Items.AIR) return;
        ItemStack template = new ItemStack(item, required);

        int existing = 0;
        for (BlockPos container : inputContainers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().is(item)) existing += slot.stack().getCount();
            }
        }
        int shortage = required - existing;
        if (shortage <= 0) return;

        ItemStack extracted = LogisticsWarehouseInventoryService.extract(level, warehousePos, template, shortage);
        if (extracted.isEmpty()) return;

        ItemStack leftover = insertIntoContainers(level, inputContainers, extracted);
        if (!leftover.isEmpty()) {
            LogisticsWarehouseInventoryService.insert(level, warehousePos, leftover);
        }
    }

    public static void storeBreedingOutputs(ServerLevel level, BlockPos pos) {
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.get(pos);
        if (data == null || !data.running()) return;

        var building = BreedingControlBoxService.resolveBuilding(level, pos);
        if (building == null) return;

        BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) return;

        BreedingDefinition definition = loadResult.definition();
        List<BlockPos> outputContainers = resolveBreedingContainerPositions(building, definition, "output", pos);
        if (outputContainers.isEmpty()) return;

        BlockPos warehousePos = nearestWarehousePos(level, building.worldOrigin());
        if (warehousePos == null) return;

        for (BlockPos container : outputContainers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().isEmpty()) continue;
                ItemStack stack = slot.stack();
                ItemStack remaining = LogisticsWarehouseInventoryService.insert(level, warehousePos, stack.copy());
                int deposited = stack.getCount() - remaining.getCount();
                if (deposited > 0) {
                    GenericContainerAccess.extractFromSlot(level, container,
                            slot.slot(), slot.access(), slot.side(), deposited,
                            s -> ItemStack.isSameItemSameComponents(s, stack));
                }
            }
        }
    }

    private static List<BlockPos> resolveBreedingContainerPositions(
            PlacedBuildingRecord building, BreedingDefinition definition,
            String containerId, BlockPos boxPos) {
        BreedingDefinition.ContainerDefinition container = definition.containers().get(containerId);
        if (container == null) return List.of();
        if ("control_box_relative".equalsIgnoreCase(container.type())) {
            int rotation = boxRotation(building.facing());
            return container.positions().stream()
                    .map(offset -> boxPos.offset(common.cn.kafei.simukraft.building.BuildingTransform.rotatePosition(offset, rotation)))
                    .map(BlockPos::immutable)
                    .toList();
        }
        if ("structure_pos".equalsIgnoreCase(container.type())) {
            return IndustrialCoordinateResolver.resolvePositions(building, container.positions());
        }
        return List.of();
    }

    private static int boxRotation(String facing) {
        if (facing == null) return 0;
        return switch (facing.toLowerCase(java.util.Locale.ROOT)) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    private static BlockPos nearestWarehousePos(ServerLevel level, BlockPos pos) {
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, pos);
        return warehouse != null ? warehouse.boxPos() : null;
    }
}
