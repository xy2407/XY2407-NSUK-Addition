package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 处理水稻作物的识别、土壤条件判定与成熟判断。 */
public final class RiceCropHelper {

    private static final ResourceLocation FD_RICE_BLOCK = ResourceLocation.tryParse("farmersdelight:rice");
    private static final int RICE_PANICLES_MAX_AGE = 3;

    private RiceCropHelper() {
    }

    public static boolean isRiceCrop(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return FD_RICE_BLOCK != null && FD_RICE_BLOCK.equals(blockId);
    }

    public static boolean isRiceSoilReady(LevelReader level, BlockPos cropPos) {
        BlockState cropState = level.getBlockState(cropPos);
        if (!cropState.isAir() && !cropState.canBeReplaced()) {
            return false;
        }
        BlockPos plantPos = cropPos.below();
        BlockState plantState = level.getBlockState(plantPos);
        boolean hasWater = plantState.is(Blocks.WATER)
                || (plantState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && plantState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));
        boolean canPlaceWater = plantState.isAir() || plantState.canBeReplaced();
        return hasWater || canPlaceWater;
    }

    public static boolean isRicePaniclesMature(LevelReader level, BlockPos cropPos, FarmCrop crop) {
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isProduce(state)) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }

        if (state.hasProperty(BlockStateProperties.AGE_3)) {
            return state.getValue(BlockStateProperties.AGE_3) >= RICE_PANICLES_MAX_AGE;
        }
        return true;
    }
}
