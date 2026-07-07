package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/** 处理右键收获类作物的识别与生长阶段重置。 */
public final class RightClickHarvestHelper {

    private static final Map<ResourceLocation, Integer> RESET_AGE = Map.of(
            ResourceLocation.tryParse("farm_and_charm:strawberry_crop"), 1,
            ResourceLocation.tryParse("kaleidoscope_cookery:tomato_crop"), 5,
            ResourceLocation.tryParse("kaleidoscope_cookery:chili_crop"), 5
    );

    private RightClickHarvestHelper() {
    }

    public static boolean isRightClickHarvestCrop(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return blockId != null && RESET_AGE.containsKey(blockId);
    }

    public static int getResetAge(FarmCrop crop) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return blockId != null ? RESET_AGE.getOrDefault(blockId, 0) : 0;
    }

    public static void resetCropAge(ServerLevel level, BlockPos pos, BlockState state, int targetAge) {
        Block block = state.getBlock();
        if (block instanceof CropBlock cropBlock) {
            level.setBlock(pos, cropBlock.getStateForAge(targetAge), Block.UPDATE_CLIENTS);
        }
    }
}
