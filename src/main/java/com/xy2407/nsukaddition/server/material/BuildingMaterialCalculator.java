package com.xy2407.nsukaddition.server.material;

import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.material.WorkMaterialPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 建造材料计算器，根据建筑结构计算总需求和剩余需求的建材分类统计。
 *  结构与方块分类结果按 (category|structureFileName) 缓存——结构 NBT 只读、材料规则解析昂贵，
 *  首次计算后纯内存聚合，避免每 tick 重新 gzip 解压结构与重复解析 WorkMaterialPolicy 规则。 */
public final class BuildingMaterialCalculator {

    public static final String OTHER_KEY = com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry.OTHER_KEY;

    private static final ConcurrentMap<String, BuildingStructure> STRUCTURE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, List<String>> BLOCK_CATEGORY_CACHE = new ConcurrentHashMap<>();

    private BuildingMaterialCalculator() {
    }

    private static String cacheKey(BuildingTaskData task) {
        return task == null ? "" : task.category() + "|" + task.structureFileName();
    }

    private static List<String> blockCategories(BuildingTaskData task) {
        if (task == null) {
            return List.of();
        }
        return BLOCK_CATEGORY_CACHE.computeIfAbsent(cacheKey(task), key -> {
            BuildingStructure structure = STRUCTURE_CACHE.computeIfAbsent(key, k ->
                    BuildingStructureService.loadStructure(task).orElse(null));
            if (structure == null) {
                return List.of();
            }
            List<String> cats = new ArrayList<>(structure.blocks().size());
            for (BuildingBlockData blockData : structure.blocks()) {
                BlockState state = blockData.state();
                if (state == null || state.isAir() || !WorkMaterialPolicy.requiresMaterial(state)) {
                    cats.add("");
                } else {
                    String categoryKey = MaterialCategoryRegistry.getCategoryKey(state.getBlock());
                    cats.add(categoryKey == null ? OTHER_KEY : categoryKey);
                }
            }
            return List.copyOf(cats);
        });
    }

    public static void invalidateMaterialCache() {
        STRUCTURE_CACHE.clear();
        BLOCK_CATEGORY_CACHE.clear();
    }

    public static Map<String, Integer> calculateRemainingRequirements(BuildingTaskData task) {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<String> cats = blockCategories(task);
        int start = Math.max(0, Math.min(task == null ? 0 : task.currentBlockIndex(), cats.size()));
        for (int i = start; i < cats.size(); i++) {
            String c = cats.get(i);
            if (!c.isEmpty()) {
                result.merge(c, 1, Integer::sum);
            }
        }
        return result;
    }

    public static Map<String, Integer> calculateTotalRequirements(BuildingTaskData task) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String c : blockCategories(task)) {
            if (!c.isEmpty()) {
                result.merge(c, 1, Integer::sum);
            }
        }
        return result;
    }

    public static String getBlockId(BlockState state) {
        if (state == null) {
            return "minecraft:air";
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "minecraft:unknown" : id.toString();
    }

    public static boolean isAir(BlockState state) {
        return state == null || state.isAir();
    }
}
