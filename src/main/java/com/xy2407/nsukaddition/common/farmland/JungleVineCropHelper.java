package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 处理丛林葡萄与可可豆（两格高，侧面附生于两格原木墙，收获两格，消耗木头）。 */
public final class JungleVineCropHelper {

    private static final String SUPPORT_LOG = "minecraft:jungle_log";
    private static final String COCOA_ID = "minecraft:cocoa";
    private static final Set<String> JUNGLE_GRAPE_IDS = Set.of(
            "vinery:jungle_grape_bush_red",
            "vinery:jungle_grape_bush_white"
    );
    private static final Direction SIDE_FACING = Direction.NORTH;
    private static final int JUNGLE_MATURE_AGE = 2;
    public static final int COCOA_MATURE_AGE = 2;

    private JungleVineCropHelper() {
    }

    public static boolean isJungleGrape(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return id != null && JUNGLE_GRAPE_IDS.contains(id.toString());
    }

    public static boolean isCocoa(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return COCOA_ID.equals(id != null ? id.toString() : null);
    }

    private static Block logBlock() {
        ResourceLocation id = ResourceLocation.tryParse(SUPPORT_LOG);
        return id != null ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    private static Item logItem() {
        ResourceLocation id = ResourceLocation.tryParse(SUPPORT_LOG);
        return id != null ? BuiltInRegistries.ITEM.get(id) : null;
    }

    public static boolean needsSidePlant(FarmlandPlot plot, ServerLevel level, BlockPos cropPos) {
        if (plot == null) {
            return false;
        }
        int baseZ = plot.min().getZ() + 1;
        if (cropPos.getZ() < baseZ) {
            return false;
        }
        if (((cropPos.getZ() - baseZ) & 1) != 0) {
            return false;
        }
        if (cropPos.getZ() >= plot.max().getZ()) {
            return false;
        }
        return isFree(level, cropPos) && isFree(level, cropPos.above());
    }

    public enum MissingKind { NONE, SEED, LOG }

    public static MissingKind missingKind(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        if (crop == null || !WorkContainerService.hasItem(level, chestPositions, crop.seed())) {
            return MissingKind.SEED;
        }
        Item log = logItem();
        if (log != null) {
            int neededLogs = 0;
            BlockPos wallBase = cropPos.relative(SIDE_FACING);
            if (needsLogAt(level, wallBase)) {
                neededLogs++;
            }
            if (needsLogAt(level, wallBase.above())) {
                neededLogs++;
            }
            if (neededLogs > 0 && countItem(level, chestPositions, log) < neededLogs) {
                return MissingKind.LOG;
            }
        }
        return MissingKind.NONE;
    }

    private static int countItem(ServerLevel level, List<BlockPos> chestPositions, Item item) {
        int count = 0;
        for (BlockPos pos : chestPositions) {
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, pos)) {
                if (slot.stack().getItem() == item) {
                    count += slot.stack().getCount();
                }
            }
        }
        return count;
    }

    private static boolean needsLogAt(ServerLevel level, BlockPos logPos) {
        BlockState state = level.getBlockState(logPos);
        if (state.is(BlockTags.JUNGLE_LOGS)) {
            return false;
        }
        return state.isAir() || state.canBeReplaced();
    }

    public static boolean plantSide(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        if (missingKind(level, chestPositions, crop, cropPos) != MissingKind.NONE) {
            return false;
        }
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            return false;
        }
        Block support = logBlock();
        if (support != null) {
            BlockPos wallBase = cropPos.relative(SIDE_FACING);
            if (!ensureLog(level, chestPositions, wallBase)) {
                return false;
            }
            if (!ensureLog(level, chestPositions, wallBase.above())) {
                return false;
            }
        }
        placeCrop(level, crop, cropPos);
        placeCrop(level, crop, cropPos.above());
        return true;
    }

    public static boolean anyMissingSupport(ServerLevel level, FarmlandPlot plot) {
        if (plot == null) {
            return false;
        }
        int baseZ = plot.min().getZ() + 1;
        int y = plot.min().getY();
        for (int x = plot.min().getX(); x <= plot.max().getX(); x++) {
            for (int z = baseZ; z < plot.max().getZ(); z += 2) {
                BlockPos cropPos = new BlockPos(x, y, z);
                if (!needsSidePlant(plot, level, cropPos)) {
                    continue;
                }
                BlockPos wallBase = cropPos.relative(SIDE_FACING);
                if (needsLogAt(level, wallBase) || needsLogAt(level, wallBase.above())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean plantSupportsOnly(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos) {
        Block support = logBlock();
        if (support == null) {
            return true;
        }
        BlockPos wallBase = cropPos.relative(SIDE_FACING);
        return ensureLog(level, chestPositions, wallBase) && ensureLog(level, chestPositions, wallBase.above());
    }

    private static void placeCrop(ServerLevel level, FarmCrop crop, BlockPos pos) {
        BlockState plant = crop.plantState();
        if (isJungleGrape(crop)) {
            if (plant.hasProperty(BlockStateProperties.NORTH)) {
                plant = plant.setValue(BlockStateProperties.NORTH, true);
            }
        } else if (isCocoa(crop)) {
            if (plant.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                plant = plant.setValue(BlockStateProperties.HORIZONTAL_FACING, SIDE_FACING);
            }
            if (plant.hasProperty(BlockStateProperties.AGE_2)) {
                plant = plant.setValue(BlockStateProperties.AGE_2, 0);
            }
        }
        level.setBlock(pos, plant, Block.UPDATE_ALL);
    }

    private static boolean ensureLog(ServerLevel level, List<BlockPos> chestPositions, BlockPos logPos) {
        BlockState state = level.getBlockState(logPos);
        if (state.is(BlockTags.JUNGLE_LOGS)) {
            return true;
        }
        if (!(state.isAir() || state.canBeReplaced())) {
            return true;
        }
        Item log = logItem();
        if (log == null || !WorkContainerService.consumeItem(level, chestPositions, log)) {
            return false;
        }
        level.setBlock(logPos, logBlock().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    public static boolean isJungleGrapeMature(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        return isJungleGrapeMatureAt(level, cropPos) && isJungleGrapeMatureAt(level, cropPos.above());
    }

    public static boolean isCocoaMature(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        return isCocoaMatureAt(level, cropPos) && isCocoaMatureAt(level, cropPos.above());
    }

    private static boolean isJungleGrapeMatureAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.AGE_3)) {
            return false;
        }
        return state.getValue(BlockStateProperties.AGE_3) >= JUNGLE_MATURE_AGE;
    }

    private static boolean isCocoaMatureAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.AGE_2)) {
            return false;
        }
        return state.getValue(BlockStateProperties.AGE_2) >= COCOA_MATURE_AGE;
    }

    public static void harvestJungleGrape(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos, BlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        collectGrape(level, drops, cropPos);
        collectGrape(level, drops, cropPos.above());
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        placeCrop(level, crop, cropPos);
        placeCrop(level, crop, cropPos.above());
    }

    private static void collectGrape(ServerLevel level, List<ItemStack> drops, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.AGE_3)) {
            return;
        }
        int age = state.getValue(BlockStateProperties.AGE_3);
        if (age < JUNGLE_MATURE_AGE) {
            return;
        }
        boolean red = state.is(redGrapeBlock());
        String produceId = red ? "vinery:jungle_grapes_red" : "vinery:jungle_grapes_white";
        int count = age >= 3 ? 2 : 1;
        ItemStack produce = ItemCropUtil.stack(produceId, count);
        if (!produce.isEmpty()) {
            drops.add(produce);
        }
    }

    private static Block redGrapeBlock() {
        ResourceLocation id = ResourceLocation.tryParse("vinery:jungle_grape_bush_red");
        return id != null ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    public static void harvestCocoa(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos, BlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        collectCocoa(level, drops, cropPos);
        collectCocoa(level, drops, cropPos.above());
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        placeCrop(level, crop, cropPos);
        placeCrop(level, crop, cropPos.above());
    }

    private static void collectCocoa(ServerLevel level, List<ItemStack> drops, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.AGE_2)) {
            return;
        }
        if (state.getValue(BlockStateProperties.AGE_2) >= COCOA_MATURE_AGE) {
            drops.addAll(Block.getDrops(state, level, pos, level.getBlockEntity(pos)));
        }
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}