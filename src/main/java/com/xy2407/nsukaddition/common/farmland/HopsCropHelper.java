package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/** 处理啤酒花这一攀爬作物的耕地需求、成熟判断与收获。 */
public final class HopsCropHelper {

    private static final String HOPS_CROP_ID = "brewery:hops_crop";
    private static final String HOPS_ITEM_ID = "brewery:hops";
    public static final int MATURE_AGE = 4;
    public static final int RESET_AGE = 1;

    private HopsCropHelper() {
    }

    public static boolean isHopsCrop(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return HOPS_CROP_ID.equals(id != null ? id.toString() : null);
    }

    public static boolean needsPlant(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        BlockState cropState = level.getBlockState(cropPos);
        if (!(cropState.isAir() || cropState.canBeReplaced())) {
            return false;
        }
        return level.getBlockState(cropPos.below()).is(Blocks.FARMLAND);
    }

    public static boolean plant(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            return false;
        }
        BlockState soilState = level.getBlockState(cropPos.below());
        if (!soilState.is(Blocks.FARMLAND)) {
            level.setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
        }
        level.setBlock(cropPos, crop.plantState(), Block.UPDATE_ALL);
        return true;
    }

    public static boolean isMature(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !HOPS_CROP_ID.equals(id.toString())) {
            return false;
        }
        return state.hasProperty(BlockStateProperties.AGE_4)
                && state.getValue(BlockStateProperties.AGE_4) >= MATURE_AGE;
    }

    public static void harvest(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos, BlockState state) {
        int count = 1 + level.random.nextInt(2);
        List<ItemStack> drops = new ArrayList<>();
        ItemStack produce = ItemCropUtil.stack(HOPS_ITEM_ID, count);
        if (!produce.isEmpty()) {
            drops.add(produce);
        }
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        BlockState reset = state.setValue(BlockStateProperties.AGE_4, RESET_AGE);
        level.setBlock(cropPos, reset, Block.UPDATE_CLIENTS);
    }
}