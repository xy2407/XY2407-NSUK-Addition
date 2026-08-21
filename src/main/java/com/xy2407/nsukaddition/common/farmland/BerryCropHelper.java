package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 处理咖啡丛与甜浆果等地面浆果作物的种植、成熟判断与采摘重置。 */
public final class BerryCropHelper {

    private static final Set<String> BERRY_BUSH_IDS = Set.of(
            "kawaiidishes:coffee_bush",
            "minecraft:sweet_berry_bush"
    );

    public static final int MATURE_AGE = 3;
    public static final int RESET_AGE = 1;

    private BerryCropHelper() {
    }

    public static boolean isBerryBush(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return id != null && BERRY_BUSH_IDS.contains(id.toString());
    }

    public static boolean needsPlant(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        BlockState cropState = level.getBlockState(cropPos);
        if (!(cropState.isAir() || cropState.canBeReplaced())) {
            return false;
        }
        BlockState soilState = level.getBlockState(cropPos.below());
        return soilState.is(BlockTags.DIRT) || soilState.is(Blocks.FARMLAND)
                || soilState.is(Blocks.GRASS_BLOCK) || soilState.is(Blocks.MUD);
    }

    public static boolean plant(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            return false;
        }
        level.setBlock(cropPos, crop.plantState(), Block.UPDATE_ALL);
        return true;
    }

    public static boolean isMature(BlockState state) {
        return state.hasProperty(BlockStateProperties.AGE_3)
                && state.getValue(BlockStateProperties.AGE_3) >= MATURE_AGE;
    }

    public static void harvest(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos, BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        String produceId;
        int min;
        int max;
        if (blockId != null && "kawaiidishes:coffee_bush".equals(blockId.toString())) {
            produceId = "kawaiidishes:coffee_berries";
            min = 1;
            max = 4;
        } else {
            produceId = "minecraft:sweet_berries";
            min = 1;
            max = 2;
        }
        int count = min + level.random.nextInt(max - min + 1);
        List<ItemStack> drops = new ArrayList<>();
        ItemStack produce = ItemCropUtil.stack(produceId, count);
        if (!produce.isEmpty()) {
            drops.add(produce);
        }
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        BlockState reset = state.getValue(BlockStateProperties.AGE_3) == MATURE_AGE
                ? state.setValue(BlockStateProperties.AGE_3, RESET_AGE) : state;
        level.setBlock(cropPos, reset, Block.UPDATE_CLIENTS);
    }
}