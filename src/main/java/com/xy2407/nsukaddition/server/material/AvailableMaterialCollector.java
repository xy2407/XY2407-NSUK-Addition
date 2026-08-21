package com.xy2407.nsukaddition.server.material;

import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 建材存量收集器，按材料分类统计指定容器集合内的物品数量，并顺带统计城市升级材料精确数。 */
public final class AvailableMaterialCollector {

    private static final TagKey<Item> STONES_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "stones"));

    private AvailableMaterialCollector() {
    }

    public static Map<String, Integer> countContainers(ServerLevel level, Collection<BlockPos> containerPositions) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (level == null || containerPositions == null) {
            return result;
        }
        int upgradeLogs = 0;
        int upgradeStone = 0;
        Set<BlockPos> counted = new HashSet<>();
        for (BlockPos pos : containerPositions) {
            if (pos == null) continue;
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, pos);
            if (!counted.add(canonical)) continue;
            if (!GenericContainerAccess.isContainer(level, canonical)) continue;
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                ItemStack stack = snapshot.stack();
                countStack(stack, result);
                if (stack != null && !stack.isEmpty() && stack.is(ItemTags.LOGS)) {
                    upgradeLogs += stack.getCount();
                }
                if (isUpgradeStone(stack)) {
                    upgradeStone += stack.getCount();
                }
            }
        }
        if (upgradeLogs > 0) {
            result.put(MaterialCategoryRegistry.UPGRADE_LOGS_KEY, upgradeLogs);
        }
        if (upgradeStone > 0) {
            result.put(MaterialCategoryRegistry.UPGRADE_STONE_KEY, upgradeStone);
        }
        return result;
    }

    private static boolean isUpgradeStone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(STONES_TAG)) {
            return true;
        }
        Item item = stack.getItem();
        return item == Items.COBBLESTONE || item == Items.STONE;
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
