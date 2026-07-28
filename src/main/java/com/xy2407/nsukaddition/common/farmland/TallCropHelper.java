package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/** 处理双层高秆作物的识别、成熟判断与收获逻辑。 */
public final class TallCropHelper {

    private static final Map<ResourceLocation, ResourceLocation> UPPER_BLOCK = Map.of(
            ResourceLocation.tryParse("culturaldelights:corn"), ResourceLocation.tryParse("culturaldelights:corn_upper")
    );

    private TallCropHelper() {
    }

    public static boolean isTallCrop(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return id != null && UPPER_BLOCK.containsKey(id);
    }

    public static boolean isUpperMature(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        ResourceLocation plantId = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        ResourceLocation upperId = UPPER_BLOCK.get(plantId);
        if (upperId == null) {
            return false;
        }
        BlockState upperState = level.getBlockState(cropPos.above());
        return upperState.is(BuiltInRegistries.BLOCK.get(upperId))
                && upperState.getBlock() instanceof CropBlock cropBlock
                && cropBlock.isMaxAge(upperState);
    }

    public static void harvestTall(ServerLevel level, BlockPos cropPos, FarmCrop crop) {

        BlockState upperState = level.getBlockState(cropPos.above());
        if (!upperState.isAir()) {
            level.destroyBlock(cropPos.above(), true);
        }

        BlockState lowerState = level.getBlockState(cropPos);
        if (!lowerState.isAir()) {
            level.destroyBlock(cropPos, true);
        }
    }
}
