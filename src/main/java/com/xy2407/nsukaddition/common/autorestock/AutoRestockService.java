package com.xy2407.nsukaddition.common.autorestock;

import com.xy2407.nsukaddition.common.breeding.BreedingBoxData;
import com.xy2407.nsukaddition.common.breeding.BreedingBoxManager;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinition;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import com.xy2407.nsukaddition.common.restaurant.CookingWorkService;
import com.xy2407.nsukaddition.common.restaurant.RestaurantBoxData;
import com.xy2407.nsukaddition.common.restaurant.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.restaurant.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.restaurant.RestaurantRecipeCacheService;
import com.xy2407.nsukaddition.common.restaurant.RestaurantDefinition;
import com.xy2407.nsukaddition.common.restaurant.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.restaurant.RestaurantRecipes;
import com.xy2407.nsukaddition.server.autorestock.WarehouseChunkLoader;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.commercial.CommercialBoxData;
import common.cn.kafei.simukraft.commercial.CommercialBoxManager;
import common.cn.kafei.simukraft.commercial.CommercialDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import common.cn.kafei.simukraft.commercial.CommercialOffer;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxManager;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialInputRequirements;
import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxData;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxManager;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingControlBoxService;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingDefinitionLoader;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingInventory;
import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 自动补货服务：工业产出存入仓库、从仓库为工业 input 容器补货、从仓库为商业补货。 */

public final class AutoRestockService {

    private static final int COMMERCIAL_MATERIAL_RADIUS_XZ = 5;
    private static final int COMMERCIAL_MATERIAL_RADIUS_Y = 2;

    private AutoRestockService() {}

    /** keepWarehousesLoaded: 为该补货盒用到的所有物流仓库的箱子区块加强加载，避免远处仓库读不到/丢料。 */
    public static void keepWarehousesLoaded(ServerLevel level, BlockPos pos) {
        for (LogisticsWarehouseData warehouse : sortedWarehouses(level, pos)) {
            WarehouseChunkLoader.forceLoad(level, warehouse);
        }
    }

    public static void storeIndustrialOutputs(ServerLevel level, BlockPos pos) {
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        IndustrialBoxData data = manager.get(pos);
        if (data == null) {
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) {
            return;
        }

        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            return;
        }

        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) {
            return;
        }
        IndustrialDefinition definition = loadResult.definition();
        if (definition == null) {
            return;
        }

        List<BlockPos> outputContainers = IndustrialControlBoxService.resolveContainerPositions(
                building, definition, "output");
        if (outputContainers.isEmpty()) {
            return;
        }

        BlockPos boxPos = building.worldOrigin();
        for (BlockPos container : outputContainers) {
            if (!level.isLoaded(container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().isEmpty()) continue;
                ItemStack stack = slot.stack();
                ItemStack remaining = insertIntoWarehouses(level, boxPos, stack.copy());
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
        if (!data.running()) {
            return;
        }

        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            return;
        }

        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) {
            return;
        }
        IndustrialDefinition definition = loadResult.definition();

        IndustrialDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null || recipe.inputs().isEmpty()) {
            return;
        }

        List<BlockPos> inputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, "input");
        if (inputContainers.isEmpty()) {
            return;
        }

        BlockPos boxPos = building.worldOrigin();
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, boxPos);
        if (warehouse == null) {
            return;
        }

        List<IndustrialDefinition.ItemRequirement> flatInputs =
                IndustrialInputRequirements.flattenItems(recipe.inputs());

        for (IndustrialDefinition.ItemRequirement input : flatInputs) {
            if (!input.consume()) {
                continue;
            }
            IndustrialItemStackSpec spec = input.spec();
            if (spec.isEmpty()) {
                continue;
            }
            int required = input.count();

            int existing = countMatchingInContainers(level, inputContainers, spec);
            int shortage = required - existing;
            if (shortage <= 0) continue;

            // 只抽输入容器实际能放下的量，避免抽出后再放不回仓库而丢料。
            int toExtract = Math.min(shortage, freeSpaceForSpec(level, inputContainers, spec));
            if (toExtract <= 0) continue;

            ItemStack extracted = extractSpecFromWarehouses(level, pos, spec, toExtract);
            if (extracted.isEmpty()) continue;

            ItemStack leftover = insertIntoContainers(level, inputContainers, extracted);
            if (!leftover.isEmpty()) {
                // 兜底：送回全部仓库，仍放不下则在盒旁掉落，绝不静默消失。
                leftover = insertIntoWarehouses(level, boxPos, leftover);
                if (!leftover.isEmpty()) {
                    dropItemStackAtBox(level, boxPos, leftover);
                }
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

    private static ItemStack extractSpecFromWarehouses(ServerLevel level, BlockPos pos,
                                                       IndustrialItemStackSpec spec, int count) {        ItemStack result = ItemStack.EMPTY;
        int remaining = count;
        for (LogisticsWarehouseData warehouse : sortedWarehouses(level, pos)) {
            if (remaining <= 0) break;
            ItemStack part = extractSpecFromWarehouse(level, warehouse, spec, remaining);
            if (part.isEmpty()) continue;
            if (result.isEmpty()) {
                result = part.copy();
            } else {
                result.grow(part.getCount());
            }
            remaining -= part.getCount();
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
        if (!data.running()) {
            return;
        }

        var building = common.cn.kafei.simukraft.building.PlacedBuildingService
                .findByContainedPosAndCategory(level, pos, "commercial", "commerce");
        if (building == null) {
            return;
        }

        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) {
            return;
        }
        CommercialDefinition definition = loadResult.definition();
        if (definition == null) {
            return;
        }

        java.util.ArrayList<ItemStack> needed = new java.util.ArrayList<>();
        for (CommercialOffer offer : definition.offers()) {
            if (offer.stock() == null) continue;
            for (CommercialOffer.MaterialRequirement mat : offer.stock().materials()) {
                Item item = mat.item();
                if (item == Items.AIR) continue;
                needed.add(new ItemStack(item, mat.count()));
            }
        }
        if (needed.isEmpty()) {
            return;
        }

        List<BlockPos> inputContainers = resolveCommercialContainerPositions(building, definition, "input");
        if (inputContainers.isEmpty()) {
            return;
        }

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
        ItemStack result = ItemStack.EMPTY;
        int remaining = count;
        for (LogisticsWarehouseData warehouse : sortedWarehouses(level, pos)) {
            if (remaining <= 0) break;
            ItemStack part = LogisticsWarehouseInventoryService.extract(level, warehouse.boxPos(), template, remaining);
            if (part.isEmpty()) continue;
            if (result.isEmpty()) {
                result = part.copy();
            } else {
                result.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return result;
    }

    private static List<LogisticsWarehouseData> sortedWarehouses(ServerLevel level, BlockPos pos) {
        UUID cityId = LogisticsControlBoxService.cityIdFor(level, pos);
        List<LogisticsWarehouseData> candidates = cityId != null
                ? LogisticsManager.get(level).warehouses(cityId)
                : LogisticsManager.get(level).warehouses().stream().toList();
        List<LogisticsWarehouseData> valid = new ArrayList<>();
        for (LogisticsWarehouseData w : candidates) {
            if (w.containers() == null || w.containers().isEmpty()) continue;
            valid.add(w);
        }
        valid.sort(Comparator.comparingDouble(w -> w.boxPos().distSqr(pos)));
        return valid;
    }

    private static LogisticsWarehouseData findNearestWarehouse(ServerLevel level, BlockPos pos) {
        List<LogisticsWarehouseData> sorted = sortedWarehouses(level, pos);
        return sorted.isEmpty() ? null : sorted.getFirst();
    }

    private static ItemStack insertIntoWarehouses(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (LogisticsWarehouseData warehouse : sortedWarehouses(level, pos)) {
            if (remaining.isEmpty()) break;
            remaining = LogisticsWarehouseInventoryService.insert(level, warehouse.boxPos(), remaining);
        }
        return remaining;
    }

    /** freeSpaceForSpec: 统计一组容器对该规格物品还能放入的总数量(满/不匹配不计)。 */
    private static int freeSpaceForSpec(ServerLevel level, List<BlockPos> containers, IndustrialItemStackSpec spec) {
        int total = 0;
        List<BlockPos> all = new ArrayList<>();
        for (BlockPos c : containers) {
            boolean dup = false;
            for (BlockPos e : all) {
                if (e.equals(c)) { dup = true; break; }
            }
            if (!dup) all.add(c);
        }
        for (BlockPos container : all) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                int maxStack = slot.stack().isEmpty() ? 64 : slot.stack().getMaxStackSize();
                if (slot.stack().isEmpty()) {
                    total += maxStack;
                } else if (spec.matches(slot.stack(), level.registryAccess())) {
                    total += Math.max(0, maxStack - slot.stack().getCount());
                }
            }
        }
        return total;
    }

    /** reinsertLeftover: 把放不进输入的余料送回仓库，仍放不下则在盒旁掉落，避免静默丢失。 */
    private static void reinsertLeftover(ServerLevel level, BlockPos boxPos, ItemStack leftover) {
        if (leftover == null || leftover.isEmpty()) return;
        ItemStack remaining = insertIntoWarehouses(level, boxPos, leftover);
        if (!remaining.isEmpty()) {
            dropItemStackAtBox(level, boxPos, remaining);
        }
    }

    /** dropItemStackAtBox: 在补货盒旁落下无法入库的物品实体，保证物品不凭空消失。 */
    private static void dropItemStackAtBox(ServerLevel level, BlockPos boxPos, ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null || boxPos == null) return;
        double x = boxPos.getX() + 0.5D;
        double y = boxPos.getY() + 1.0D;
        double z = boxPos.getZ() + 0.5D;
        ItemEntity entity = new ItemEntity(level, x, y, z, stack.copy());
        entity.setDeltaMovement(0.0D, 0.1D, 0.0D);
        level.addFreshEntity(entity);
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

    public static void restockBreedingInputs(ServerLevel level, BlockPos pos) {
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.get(pos);
        if (data == null || !data.running()) {
            return;
        }

        var building = BreedingControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            return;
        }

        BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) {
            return;
        }

        BreedingDefinition definition = loadResult.definition();
        BreedingDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null) {
            return;
        }

        String feedItemId = recipe.effectiveFeedItem();
        if (feedItemId.isBlank()) {
            return;
        }

        List<BlockPos> inputContainers = resolveBreedingContainerPositions(building, definition, "input", pos);
        if (inputContainers.isEmpty()) {
            return;
        }

        BlockPos boxPos = building.worldOrigin();
        int required = 16;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.tryParse(feedItemId));
        if (item == null || item == Items.AIR) {
            return;
        }
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

        ItemStack extracted = extractFromNearestWarehouse(level, boxPos, template, shortage);
        if (extracted.isEmpty()) return;

        ItemStack leftover = insertIntoContainers(level, inputContainers, extracted);
        if (!leftover.isEmpty()) {
            reinsertLeftover(level, boxPos, leftover);
        }
    }

    public static void restockRestaurantInputs(ServerLevel level, BlockPos pos) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.get(pos);
        if (data == null) {
            AutoRestockConfig.remove(level, pos);
            return;
        }
        if (!data.running()) {
            return;
        }

        PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            return;
        }
        RestaurantDefinitionLoader.LoadResult loadResult = RestaurantDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) {
            return;
        }
        RestaurantDefinition definition = loadResult.definition();

        Set<String> cookItems = data.selectedCookItems();
        if (cookItems.isEmpty()) {
            return;
        }

        List<BlockPos> inputContainers = CookingWorkService.resolvePositions(building, definition, "input", pos);
        if (inputContainers.isEmpty()) {
            return;
        }

        BlockPos boxPos = building.worldOrigin();
        final int targetStockPerInput = 16;

        // 把每个菜品的直接材料递归解构成最基础原料再补给，仓库只需备基础料、无需备中间合成物。
        // 整菜解析结果全局缓存(最多70道，超出淘汰最早)，避免每次补货重复全表扫配方。
        java.util.LinkedHashMap<Ingredient, Integer> neededByIngredient = new java.util.LinkedHashMap<>();
        for (String itemId : cookItems) {
            for (Ingredient leaf : RestaurantRecipeCacheService.dishMaterials(level, itemId)) {
                if (leaf == null || leaf.isEmpty()) { continue; }
                neededByIngredient.merge(leaf, targetStockPerInput, Math::max);
            }
        }
        if (neededByIngredient.isEmpty()) {
            return;
        }

        for (var entry : neededByIngredient.entrySet()) {
            Ingredient ing = entry.getKey();
            int required = entry.getValue();
            int existing = countMatchingInInputContainers(level, inputContainers, ing);
            int shortage = required - existing;
            if (shortage <= 0) continue;

            ItemStack extracted = extractIngredientFromWarehouses(level, boxPos, ing, shortage);
            if (extracted.isEmpty()) {
                continue;
            }

            ItemStack leftover = insertIntoContainers(level, inputContainers, extracted);
            if (!leftover.isEmpty()) {
                reinsertLeftover(level, boxPos, leftover);
            }
        }
    }

    private static int countItemInInputContainers(ServerLevel level, List<BlockPos> containers, Item item) {
        int count = 0;
        for (BlockPos container : containers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!slot.stack().isEmpty() && slot.stack().is(item)) count += slot.stack().getCount();
            }
        }
        return count;
    }

    private static ItemStack extractItemFromWarehouses(ServerLevel level, BlockPos pos, Item item, int count) {
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return extractIngredientFromWarehouses(level, pos, Ingredient.of(item), count);
    }

    private static int countMatchingInInputContainers(ServerLevel level, List<BlockPos> containers, Ingredient ing) {
        int count = 0;
        for (BlockPos container : containers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!slot.stack().isEmpty() && ing.test(slot.stack())) count += slot.stack().getCount();
            }
        }
        return count;
    }

    private static ItemStack extractIngredientFromWarehouses(ServerLevel level, BlockPos pos,
                                                             Ingredient ing, int count) {
        ItemStack result = ItemStack.EMPTY;
        int remaining = count;
        for (LogisticsWarehouseData warehouse : sortedWarehouses(level, pos)) {
            if (remaining <= 0) break;
            ItemStack part = extractIngredientFromWarehouse(level, warehouse, ing, remaining);
            if (part.isEmpty()) continue;
            if (result.isEmpty()) {
                result = part.copy();
            } else {
                result.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return result;
    }

    private static ItemStack extractIngredientFromWarehouse(ServerLevel level, LogisticsWarehouseData warehouse,
                                                             Ingredient ing, int count) {
        if (warehouse == null) return ItemStack.EMPTY;

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
                if (slot.stack().isEmpty() || !ing.test(slot.stack())) continue;
                int amount = Math.min(remaining, slot.stack().getCount());
                ItemStack extracted = GenericContainerAccess.extractFromSlot(level, canonical,
                        slot.slot(), slot.access(), slot.side(), amount,
                        ing::test);
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

    public static void storeBreedingOutputs(ServerLevel level, BlockPos pos) {
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.get(pos);
        if (data == null || !data.running()) {
            return;
        }

        var building = BreedingControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            return;
        }

        BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid() || loadResult.definition() == null) {
            return;
        }

        BreedingDefinition definition = loadResult.definition();
        List<BlockPos> outputContainers = resolveBreedingContainerPositions(building, definition, "output", pos);
        if (outputContainers.isEmpty()) {
            return;
        }

        for (BlockPos container : outputContainers) {
            if (!level.isLoaded(container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().isEmpty()) continue;
                ItemStack stack = slot.stack();
                ItemStack remaining = insertIntoWarehouses(level, building.worldOrigin(), stack.copy());
                int deposited = stack.getCount() - remaining.getCount();
                if (deposited > 0) {
                    GenericContainerAccess.extractFromSlot(level, container,
                            slot.slot(), slot.access(), slot.side(), deposited,
                            s -> ItemStack.isSameItemSameComponents(s, stack));
                }
            }
        }
    }

    /** storeMineralOutputs: 将钻井平台产出木桶内的矿物存入物流仓库(入库)。 */
    public static void storeMineralOutputs(ServerLevel level, BlockPos pos) {
        MineralDrillingBoxData data = MineralDrillingBoxManager.get(level).get(pos);
        if (data == null || !data.running()) {
            return;
        }
        PlacedBuildingRecord building = MineralDrillingControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            return;
        }
        for (BlockPos container : resolveMineralOutputPositions(level, building)) {
            if (!level.isLoaded(container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().isEmpty()) {
                    continue;
                }
                ItemStack stack = slot.stack();
                ItemStack remaining = insertIntoWarehouses(level, building.worldOrigin(), stack.copy());
                int deposited = stack.getCount() - remaining.getCount();
                if (deposited > 0) {
                    GenericContainerAccess.extractFromSlot(level, container,
                            slot.slot(), slot.access(), slot.side(), deposited,
                            s -> ItemStack.isSameItemSameComponents(s, stack));
                }
            }
        }
    }

    /** restockMineralTools: 从仓库补钻杆段与钻头到钻井双槽；钻头按目标深度选浅/深(>=10 浅层，否则深层)。 */
    public static void restockMineralTools(ServerLevel level, BlockPos pos) {
        MineralDrillingBoxData data = MineralDrillingBoxManager.get(level).get(pos);
        if (data == null) {
            return;
        }
        MineralDrillingInventory inv = data.inventory();
        PlacedBuildingRecord building = MineralDrillingControlBoxService.resolveBuilding(level, pos);
        BlockPos boxPos = building != null ? building.worldOrigin() : pos;

        int targetRod = 16;
        int existingRod = inv.getItem(MineralDrillingInventory.DRILL_ROD_SLOT).getCount();
        int rodShortage = targetRod - existingRod;
        if (rodShortage > 0) {
            ItemStack rod = new ItemStack(ModItems.DRILL_ROD_SEGMENT.get(), rodShortage);
            ItemStack extracted = extractFromNearestWarehouse(level, boxPos, rod, rodShortage);
            if (!extracted.isEmpty()) {
                inv.setItem(MineralDrillingInventory.DRILL_ROD_SLOT,
                        new ItemStack(ModItems.DRILL_ROD_SEGMENT.get(),
                                existingRod + extracted.getCount()));
            }
        }

        if (inv.getItem(MineralDrillingInventory.DRILL_BIT_SLOT).isEmpty()) {
            int depth = data.drillDepth();
            Item bitItem = depth >= MineralDrillingControlBoxService.SHALLOW_DRILL_MIN_Y
                    ? ModItems.SHALLOW_DRILL_BIT.get()
                    : ModItems.DEEP_DRILL_BIT.get();
            ItemStack extracted = extractItemFromWarehouses(level, boxPos, bitItem, 1);
            if (!extracted.isEmpty()) {
                inv.setItem(MineralDrillingInventory.DRILL_BIT_SLOT, extracted);
            }
        }
    }

    /** resolveMineralOutputPositions: 优先用钻井 JSON 声明的产出容器坐标；无/空(含 JSON 错误)时回退扫描平台内任意容器方块。 */
    private static List<BlockPos> resolveMineralOutputPositions(ServerLevel level, PlacedBuildingRecord building) {
        if (building == null) {
            return List.of();
        }
        MineralDrillingDefinitionLoader.OutputContainerResolution resolution =
                MineralDrillingDefinitionLoader.resolveOutputContainers(building);
        List<BlockPos> out = new ArrayList<>();
        if (resolution.declared() && !resolution.positions().isEmpty()) {
            for (BlockPos p : resolution.positions()) {
                if (p != null) {
                    out.add(p.immutable());
                }
            }
            return out;
        }
        // declared 但为空(如 JSON 格式错误被 loader 吞掉)或 legacy：扫描平台内所有"容器方块"(木桶/箱子等)作为产出容器。
        if (building.blocks() != null) {
            LinkedHashSet<BlockPos> seen = new LinkedHashSet<>();
            for (BuildingBlockData block : building.blocks()) {
                if (block == null || block.relativePos() == null) {
                    continue;
                }
                BlockPos world = building.worldOrigin() != null
                        ? building.worldOrigin().offset(block.relativePos())
                        : block.relativePos();
                if (level.isLoaded(world) && level.getBlockEntity(world) instanceof net.minecraft.world.Container) {
                    seen.add(world.immutable());
                }
            }
            out.addAll(seen);
        }
        return out;
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

    public static void restockFarmlandInputs(ServerLevel level, BlockPos pos) {
        FarmlandBoxData data = FarmlandBoxManager.get(level).get(pos);
        if (data == null || !data.running()) {
            return;
        }

        FarmCrop crop = data.crop();
        if (crop == null) {
            return;
        }

        List<BlockPos> chests = FarmlandBoxService.resolveAdjacentChests(level, pos);
        if (chests.isEmpty()) {
            return;
        }

        Item seed = crop.seed();
        if (seed != null && seed != Items.AIR) {
            int existing = countItemInContainers(level, chests, seed);
            int shortage = 16 - existing;
            if (shortage > 0) {
                ItemStack template = new ItemStack(seed, shortage);
                ItemStack extracted = extractFromNearestWarehouse(level, pos, template, shortage);
                if (!extracted.isEmpty()) {
                    ItemStack leftover = insertIntoContainers(level, chests, extracted);
                    if (!leftover.isEmpty()) {
                        reinsertLeftover(level, pos, leftover);
                    }
                }
            }
        }

        int existingBoneMeal = countItemInContainers(level, chests, Items.BONE_MEAL);
        int boneMealShortage = 16 - existingBoneMeal;
        if (boneMealShortage > 0) {
            ItemStack template = new ItemStack(Items.BONE_MEAL, boneMealShortage);
            ItemStack extracted = extractFromNearestWarehouse(level, pos, template, boneMealShortage);
            if (!extracted.isEmpty()) {
                ItemStack leftover = insertIntoContainers(level, chests, extracted);
                if (!leftover.isEmpty()) {
                    reinsertLeftover(level, pos, leftover);
                }
            }
        }
    }

    public static void storeFarmlandOutputs(ServerLevel level, BlockPos pos) {
        FarmlandBoxData data = FarmlandBoxManager.get(level).get(pos);
        if (data == null || !data.running()) {
            return;
        }

        FarmCrop crop = data.crop();
        List<BlockPos> chests = FarmlandBoxService.resolveAdjacentChests(level, pos);
        if (chests.isEmpty()) {
            return;
        }

        Item seed = crop != null ? crop.seed() : null;

        int seedReserve = 128;
        int totalSeeds = 0;
        if (seed != null && seed != Items.AIR) {
            for (BlockPos chest : chests) {
                if (!level.isLoaded(chest)) continue;
                for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, chest)) {
                    if (slot.stack().isEmpty()) continue;
                    if (slot.stack().is(seed)) totalSeeds += slot.stack().getCount();
                }
            }
        }
        int seedToMove = Math.max(0, totalSeeds - seedReserve);
        int seedMoved = 0;

        for (BlockPos chest : chests) {
            if (!level.isLoaded(chest)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, chest)) {
                if (slot.stack().isEmpty()) continue;
                ItemStack stack = slot.stack();

                if (stack.getItem() == Items.BONE_MEAL) continue;

                if (seed != null && stack.is(seed)) {
                    if (seedMoved >= seedToMove) continue;
                    int moveCount = Math.min(stack.getCount(), seedToMove - seedMoved);
                    if (moveCount <= 0) continue;
                    ItemStack toMove = stack.copyWithCount(moveCount);
                    ItemStack remaining = insertIntoWarehouses(level, pos, toMove);
                    int deposited = moveCount - remaining.getCount();
                    if (deposited > 0) {
                        GenericContainerAccess.extractFromSlot(level, chest,
                                slot.slot(), slot.access(), slot.side(), deposited,
                                s -> ItemStack.isSameItemSameComponents(s, stack));
                        seedMoved += deposited;
                    }
                    continue;
                }

                ItemStack remaining = insertIntoWarehouses(level, pos, stack.copy());
                int deposited = stack.getCount() - remaining.getCount();
                if (deposited > 0) {
                    GenericContainerAccess.extractFromSlot(level, chest,
                            slot.slot(), slot.access(), slot.side(), deposited,
                            s -> ItemStack.isSameItemSameComponents(s, stack));
                }
            }
        }
    }

    private static int countItemInContainers(ServerLevel level, List<BlockPos> containers, Item item) {
        int count = 0;
        for (BlockPos container : containers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (slot.stack().is(item)) count += slot.stack().getCount();
            }
        }
        return count;
    }
}