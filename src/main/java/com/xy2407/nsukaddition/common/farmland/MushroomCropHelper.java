package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.List;

/**
 * 农田盒的蘑菇种植适配：用农夫乐事肥沃土壤(rich_soil)当地基，先统一铺好地基再种原版蘑菇；
 * 蘑菇被肥沃土自然转成蘑菇菌落(colony)，收获时收 colony 并重置 age 保留菌落，避免 simukraft
 * 误判"作物丢失"导致循环补种。
 */
public final class MushroomCropHelper {

    private static final String RICH_SOIL_ID = "farmersdelight:rich_soil";
    private static final String BROWN_MUSHROOM_ID = "minecraft:brown_mushroom";
    private static final String RED_MUSHROOM_ID = "minecraft:red_mushroom";
    private static final String BROWN_COLONY_ID = "farmersdelight:brown_mushroom_colony";
    private static final String RED_COLONY_ID = "farmersdelight:red_mushroom_colony";

    private MushroomCropHelper() {
    }

    public static boolean isBrownMushroom(FarmCrop crop) {
        return isPlant(crop, BROWN_MUSHROOM_ID);
    }

    public static boolean isRedMushroom(FarmCrop crop) {
        return isPlant(crop, RED_MUSHROOM_ID);
    }

    public static boolean isMushroomCrop(FarmCrop crop) {
        return isBrownMushroom(crop) || isRedMushroom(crop);
    }

    private static boolean isPlant(FarmCrop crop, String blockId) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return id != null && blockId.equals(id.toString());
    }

    private static Block block(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc != null ? BuiltInRegistries.BLOCK.get(loc) : null;
    }

    private static Item item(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc != null ? BuiltInRegistries.ITEM.get(loc) : null;
    }

    private static Block richSoilBlock() {
        return block(RICH_SOIL_ID);
    }

    private static Block colonyBlock(FarmCrop crop) {
        return block(isRedMushroom(crop) ? RED_COLONY_ID : BROWN_COLONY_ID);
    }

    private static boolean isRichSoil(BlockState state) {
        Block soil = richSoilBlock();
        return soil != null && state.is(soil);
    }

    public static boolean anyMissingSoil(ServerLevel level, FarmlandPlot plot, BlockPos boxPos) {
        if (plot == null) {
            return false;
        }
        int y = plot.min().getY();
        for (int x = plot.min().getX(); x <= plot.max().getX(); x++) {
            for (int z = plot.min().getZ(); z <= plot.max().getZ(); z++) {
                if (boxPos != null && boxPos.getX() == x && boxPos.getZ() == z) {
                    continue;
                }
                BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
                if (!isRichSoil(below)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean placeSoilOnly(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos) {
        BlockPos below = cropPos.below();
        BlockState belowState = level.getBlockState(below);
        if (isRichSoil(belowState)) {
            return true;
        }
        Item soilItem = item(RICH_SOIL_ID);
        if (soilItem == null || !WorkContainerService.consumeItem(level, chestPositions, soilItem)) {
            return false;
        }
        Block soilBlock = richSoilBlock();
        if (soilBlock == null) {
            return false;
        }
        if (!belowState.isAir() && !belowState.canBeReplaced()) {
            List<ItemStack> drops = Block.getDrops(belowState, level, below, level.getBlockEntity(below));
            level.setBlock(below, soilBlock.defaultBlockState(), 3);
            WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, below);
            return true;
        }
        level.setBlock(below, soilBlock.defaultBlockState(), 3);
        return true;
    }

    public static boolean harvestColony(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        BlockState state = level.getBlockState(cropPos);
        Block colony = colonyBlock(crop);
        if (colony == null || !state.is(colony)) {
            return false;
        }
        IntegerProperty ageProp = state.hasProperty(BlockStateProperties.AGE_3) ? BlockStateProperties.AGE_3 : null;
        int age = ageProp != null ? state.getValue(ageProp) : 0;
        if (age < 3) {
            return false;
        }
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, level, cropPos, level.getBlockEntity(cropPos));
        BlockState kept = ageProp != null ? state.setValue(ageProp, 0) : state;
        level.setBlock(cropPos, kept, 3);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        return true;
    }

    public static boolean isColonyHarvestable(ServerLevel level, FarmCrop crop, BlockPos cropPos) {
        BlockState state = level.getBlockState(cropPos);
        Block colony = colonyBlock(crop);
        if (colony == null || !state.is(colony)) {
            return false;
        }
        if (!state.hasProperty(BlockStateProperties.AGE_3)) {
            return false;
        }
        return state.getValue(BlockStateProperties.AGE_3) >= 3;
    }

    public static boolean hasAnyCrop(ServerLevel level, BlockPos cropPos) {
        BlockState state = level.getBlockState(cropPos);
        Block brown = block(BROWN_MUSHROOM_ID);
        Block red = block(RED_MUSHROOM_ID);
        Block brownColony = block(BROWN_COLONY_ID);
        Block redColony = block(RED_COLONY_ID);
        return (brown != null && state.is(brown))
                || (red != null && state.is(red))
                || (brownColony != null && state.is(brownColony))
                || (redColony != null && state.is(redColony));
    }
}