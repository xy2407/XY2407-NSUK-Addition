package com.xy2407.nsukaddition.server.material;

import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** 建造材料计算器，根据建筑结构计算总需求和剩余需求的建材分类统计。 */
public final class BuildingMaterialCalculator {

    public static final String OTHER_KEY = com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry.OTHER_KEY;

    private BuildingMaterialCalculator() {
    }

    public static Map<String, Integer> calculateRemainingRequirements(BuildingTaskData task) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (task == null) {
            return result;
        }

        Optional<BuildingStructure> structureOpt = BuildingStructureService.loadStructure(task);
        if (structureOpt.isEmpty()) {
            return result;
        }

        List<BuildingBlockData> blocks = structureOpt.get().blocks();
        int start = Math.max(0, Math.min(task.currentBlockIndex(), blocks.size()));

        for (int i = start; i < blocks.size(); i++) {
            BuildingBlockData blockData = blocks.get(i);
            BlockState state = blockData.state();
            if (state == null || state.isAir()) {
                continue;
            }

            String categoryKey = MaterialCategoryRegistry.getCategoryKey(state.getBlock());
            if (categoryKey == null) {
                categoryKey = OTHER_KEY;
            }
            result.merge(categoryKey, 1, Integer::sum);
        }

        return result;
    }

    public static Map<String, Integer> calculateTotalRequirements(BuildingTaskData task) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (task == null) {
            return result;
        }

        Optional<BuildingStructure> structureOpt = BuildingStructureService.loadStructure(task);
        if (structureOpt.isEmpty()) {
            return result;
        }

        for (BuildingBlockData blockData : structureOpt.get().blocks()) {
            BlockState state = blockData.state();
            if (state == null || state.isAir()) {
                continue;
            }

            String categoryKey = MaterialCategoryRegistry.getCategoryKey(state.getBlock());
            if (categoryKey == null) {
                categoryKey = OTHER_KEY;
            }
            result.merge(categoryKey, 1, Integer::sum);
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
