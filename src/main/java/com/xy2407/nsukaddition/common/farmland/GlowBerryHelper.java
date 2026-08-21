package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
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

import java.util.ArrayList;
import java.util.List;

/** 处理发光浆果悬挂藤蔓作物的种植与收获（原木第3格支撑，藤第1+2格，收获两格，消耗木头）。 */
public final class GlowBerryHelper {

    private static final String CAVE_VINES = "minecraft:cave_vines";
    private static final String CAVE_VINES_PLANT = "minecraft:cave_vines_plant";
    private static final String SUPPORT_LOG = "minecraft:jungle_log";

    private GlowBerryHelper() {
    }

    public static boolean isGlowBerry(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return CAVE_VINES.equals(id != null ? id.toString() : null);
    }

    public static boolean needsPlant(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        return isFree(level, cropPos) && isFree(level, cropPos.above());
    }

    public static boolean plant(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            return false;
        }
        BlockPos logPos = cropPos.above(2);
        if (!isLog(level, logPos) && !consumeLog(level, chestPositions)) {
            return false;
        }
        if (!isLog(level, logPos)) {
            setLog(level, logPos);
        }
        setBlock(level, cropPos, plantBlock());
        setVineBerry(level, cropPos.above(), crop);
        return true;
    }

    public static boolean needsLog(ServerLevel level, BlockPos cropPos) {
        return !isLog(level, cropPos.above(2));
    }

    public static boolean hasLog(ServerLevel level, List<BlockPos> chestPositions) {
        Item logItem = item(SUPPORT_LOG);
        return logItem != null && WorkContainerService.hasItem(level, chestPositions, logItem);
    }

    public static boolean isMature(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        BlockState vine = level.getBlockState(cropPos.above());
        if (!vine.is(block(CAVE_VINES)) || !vine.hasProperty(BlockStateProperties.BERRIES)) {
            return false;
        }
        return vine.getValue(BlockStateProperties.BERRIES);
    }

    public static void harvest(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos, BlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        collect(level, drops, cropPos.above());
        collect(level, drops, cropPos);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        setBlock(level, cropPos, plantBlock());
        setVineBerry(level, cropPos.above(), crop);
    }

    private static void collect(ServerLevel level, List<ItemStack> drops, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            drops.addAll(Block.getDrops(state, level, pos, level.getBlockEntity(pos)));
        }
    }

    private static boolean setVineBerry(ServerLevel level, BlockPos pos, FarmCrop crop) {
        BlockState vine = crop.plantState();
        if (vine.hasProperty(BlockStateProperties.BERRIES)) {
            vine = vine.setValue(BlockStateProperties.BERRIES, true);
        }
        level.setBlock(pos, vine, Block.UPDATE_ALL);
        return true;
    }

    private static void setBlock(ServerLevel level, BlockPos pos, Block block) {
        if (block != null) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static Block plantBlock() {
        return block(CAVE_VINES_PLANT);
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isLog(ServerLevel level, BlockPos pos) {
        Block support = block(SUPPORT_LOG);
        return support != null && level.getBlockState(pos).is(support);
    }

    private static void setLog(ServerLevel level, BlockPos pos) {
        Block support = block(SUPPORT_LOG);
        if (support != null) {
            level.setBlock(pos, support.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean consumeLog(ServerLevel level, List<BlockPos> chestPositions) {
        Item logItem = item(SUPPORT_LOG);
        return logItem != null && WorkContainerService.consumeItem(level, chestPositions, logItem);
    }

    private static Block block(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc != null && BuiltInRegistries.BLOCK.containsKey(loc) ? BuiltInRegistries.BLOCK.get(loc) : null;
    }

    private static Item item(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc != null && BuiltInRegistries.ITEM.containsKey(loc) ? BuiltInRegistries.ITEM.get(loc) : null;
    }
}