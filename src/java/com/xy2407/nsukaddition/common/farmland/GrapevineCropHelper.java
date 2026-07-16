package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;
import java.util.Set;

/** 处理葡萄藤架作物的种植、支架搭建、成熟判断与收获。 */
public final class GrapevineCropHelper {

    private static final Map<String, String> CROP_TO_TRELLIS = Map.of(
            "kaleidoscope_tavern:grape_crop",      "kaleidoscope_tavern:grapevine_trellis",
            "kaleidoscope_tavern:ice_grape_crop",  "kaleidoscope_tavern:ice_grapevine_trellis",
            "kaleidoscope_tavern:gold_grape_crop", "kaleidoscope_tavern:gold_grapevine_trellis"
    );

    private static final String TRELLIS_ID = "kaleidoscope_tavern:trellis";

    private static final Set<String> CROP_IDS = CROP_TO_TRELLIS.keySet();

    public static final int MATURE_AGE = 5;

    private GrapevineCropHelper() {}

    public static boolean isGrapevineCrop(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return id != null && CROP_IDS.contains(id.toString());
    }

    public static Block getTrellisBlock(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) return null;
        ResourceLocation cropId = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        if (cropId == null) return null;
        String trellisStr = CROP_TO_TRELLIS.get(cropId.toString());
        if (trellisStr == null) return null;
        ResourceLocation trellisId = ResourceLocation.tryParse(trellisStr);
        if (trellisId == null || !BuiltInRegistries.BLOCK.containsKey(trellisId)) return null;
        return BuiltInRegistries.BLOCK.get(trellisId);
    }

    public static Block getPillarBlock() {
        ResourceLocation id = ResourceLocation.tryParse(TRELLIS_ID);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
        return BuiltInRegistries.BLOCK.get(id);
    }

    public static boolean isPlotCorner(FarmlandPlot plot, BlockPos cropPos) {
        if (plot == null) return false;
        int minX = plot.min().getX();
        int maxX = plot.max().getX();
        int minZ = plot.min().getZ();
        int maxZ = plot.max().getZ();
        return (cropPos.getX() == minX || cropPos.getX() == maxX)
                && (cropPos.getZ() == minZ || cropPos.getZ() == maxZ);
    }

    public static boolean needsPlant(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        Block trellisBlock = getTrellisBlock(crop);
        if (trellisBlock == null) return false;
        BlockState above2 = level.getBlockState(cropPos.above(2));

        if (above2.is(trellisBlock)) return false;
        return above2.isAir() || above2.canBeReplaced();
    }

    public static void plant(ServerLevel level, BlockPos cropPos, FarmCrop crop, FarmlandPlot plot) {

        Block trellisBlock = getTrellisBlock(crop);
        if (trellisBlock != null) {
            level.setBlock(cropPos.above(2), trellisBlock.defaultBlockState(), Block.UPDATE_ALL);
        }

        if (plot != null && isPlotCorner(plot, cropPos)) {
            Block pillar = getPillarBlock();
            if (pillar != null) {
                BlockState current0 = level.getBlockState(cropPos);
                if (current0.isAir() || current0.canBeReplaced()) {
                    level.setBlock(cropPos, pillar.defaultBlockState(), Block.UPDATE_ALL);
                }
                BlockState current1 = level.getBlockState(cropPos.above());
                if (current1.isAir() || current1.canBeReplaced()) {
                    level.setBlock(cropPos.above(), pillar.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    public static boolean isMature(ServerLevel level, BlockPos cropPos, FarmCrop crop) {
        BlockState state = level.getBlockState(cropPos.above());
        if (!state.is(crop.plantBlock())) return false;
        if (!state.hasProperty(BlockStateProperties.AGE_5)) return false;
        return state.getValue(BlockStateProperties.AGE_5) >= MATURE_AGE;
    }

    public static void harvest(ServerLevel level, BlockPos cropPos) {
        level.removeBlock(cropPos.above(), false);
    }

    public static BlockState getCropState(ServerLevel level, BlockPos cropPos) {
        return level.getBlockState(cropPos.above());
    }
}
