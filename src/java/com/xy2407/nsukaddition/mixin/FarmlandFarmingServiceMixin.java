package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.WorkContainerService;
import com.xy2407.nsukaddition.common.farmland.FarmlandWorkResultAccess;
import com.xy2407.nsukaddition.common.farmland.GrapeBushHelper;
import com.xy2407.nsukaddition.common.farmland.GrapevineCropHelper;
import com.xy2407.nsukaddition.common.farmland.RiceCropHelper;
import com.xy2407.nsukaddition.common.farmland.RightClickHarvestHelper;
import com.xy2407.nsukaddition.common.farmland.TallCropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 扩展农田种植/收获逻辑，支持水稻、葡萄等特殊作物。 */
@Mixin(FarmlandFarmingService.class)
public class FarmlandFarmingServiceMixin {

    private static final class WorkPhaseRef {
        private static final Object PLANT;
        private static final Object HARVEST;
        private static final Object BONEMEAL;

        static {
            try {
                Class<?> enumClass = Class.forName("common.cn.kafei.simukraft.farmland.FarmlandWorkPhase");
                Object[] constants = enumClass.getEnumConstants();
                Object plant = null, harvest = null, bonemeal = null;
                for (Object c : constants) {
                    String name = ((Enum<?>) c).name();
                    if ("PLANT".equals(name)) plant = c;
                    else if ("HARVEST".equals(name)) harvest = c;
                    else if ("BONEMEAL".equals(name)) bonemeal = c;
                }
                PLANT = plant;
                HARVEST = harvest;
                BONEMEAL = bonemeal;
            } catch (Exception e) {
                throw new RuntimeException("反射访问 FarmlandWorkPhase 失败", e);
            }
        }

        static boolean isPlantOrHarvestOrBonemeal(Object phase) {
            return phase == PLANT || phase == HARVEST || phase == BONEMEAL;
        }
    }

    @Inject(method = "needsWaterWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$riceSkipWaterWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                   BlockPos cropPos, CallbackInfoReturnable<Boolean> cir) {
        if (RiceCropHelper.isRiceCrop(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsTillWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$riceSkipTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (RiceCropHelper.isRiceCrop(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$ricePlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                    CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!RiceCropHelper.isRiceCrop(crop)) {
            return;
        }
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(RiceCropHelper.isRiceSoilReady(level, cropPos));
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$ricePlantInWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                 BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!RiceCropHelper.isRiceCrop(crop)) {
            return;
        }
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        BlockPos plantPos = cropPos.below();
        BlockState waterState = level.getBlockState(plantPos);
        if (!waterState.is(Blocks.WATER) && !(waterState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && waterState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))) {
            if (!waterState.isAir() && !waterState.canBeReplaced()) {
                List<ItemStack> drops = Block.getDrops(waterState, level, plantPos, level.getBlockEntity(plantPos));
                level.setBlock(plantPos, Blocks.WATER.defaultBlockState(), 3);
                WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, plantPos);
            } else {
                level.setBlock(plantPos, Blocks.WATER.defaultBlockState(), 3);
            }
        }
        ensureSoilBelow(level, data.boxPos(), plantPos.below(), chestPositions);

        BlockState riceState = crop.plantState();
        if (riceState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            riceState = riceState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        }
        level.setBlock(plantPos, riceState, 3);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$riceHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                      CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!RiceCropHelper.isRiceCrop(crop)) {
            return;
        }
        cir.setReturnValue(RiceCropHelper.isRicePaniclesMature(level, cropPos, crop));
    }

    @Inject(method = "needsBonemealWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$riceBonemealCondition(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                       BlockPos cropPos, CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!RiceCropHelper.isRiceCrop(crop)) {
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isOwnPlant(state) || crop.isMatureFull(state)) {
            cir.setReturnValue(false);
            return;
        }
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b)) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(b.isValidBonemealTarget(level, cropPos, state) && WorkContainerService.hasItem(level, chestPositions, net.minecraft.world.item.Items.BONE_MEAL));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$riceHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                            BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!RiceCropHelper.isRiceCrop(crop)) {
            return;
        }
        if (!RiceCropHelper.isRicePaniclesMature(level, cropPos, crop)) {
            cir.setReturnValue(FarmlandWorkResultAccess.processed());
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        List<net.minecraft.world.item.ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, level, cropPos, level.getBlockEntity(cropPos));
        level.removeBlock(cropPos, false);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "scanCellCount", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$fullScanForPlantHarvest(FarmlandPlot plot, @Coerce Object phase,
                                                        CallbackInfoReturnable<Integer> cir) {
        if (WorkPhaseRef.isPlantOrHarvestOrBonemeal(phase)) {
            cir.setReturnValue(Math.max(1, plot.cellCount()));
        }
    }

    @Inject(method = "scanCellAt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$fullScanCellAt(FarmlandPlot plot, @Coerce Object phase, int index,
                                                CallbackInfoReturnable<BlockPos> cir) {
        if (WorkPhaseRef.isPlantOrHarvestOrBonemeal(phase)) {
            cir.setReturnValue(plot.cellAt(index));
        }
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$tallCropHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                         CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!TallCropHelper.isTallCrop(crop)) {
            return;
        }
        cir.setReturnValue(TallCropHelper.isUpperMature(level, cropPos, crop));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$tallCropHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!TallCropHelper.isTallCrop(crop)) {
            return;
        }
        if (!TallCropHelper.isUpperMature(level, cropPos, crop)) {
            return;
        }
        BlockState upperState = level.getBlockState(cropPos.above());
        List<net.minecraft.world.item.ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                upperState, level, cropPos.above(), level.getBlockEntity(cropPos.above()));
        level.removeBlock(cropPos.above(), false);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$rightClickHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                  BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!RightClickHarvestHelper.isRightClickHarvestCrop(crop)) {
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isMatureFull(state)) {
            return;
        }
        List<net.minecraft.world.item.ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, level, cropPos, level.getBlockEntity(cropPos));
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        RightClickHarvestHelper.resetCropAge(level, cropPos, state, RightClickHarvestHelper.getResetAge(crop));
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsTillWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapeBushSkipTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (GrapeBushHelper.isGrapeBush(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapeBushPlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                        CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!GrapeBushHelper.isGrapeBush(crop)) return;
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        BlockState cropState = level.getBlockState(cropPos);
        BlockState soilState = level.getBlockState(cropPos.below());
        boolean cellFree = cropState.isAir() || cropState.canBeReplaced();
        boolean soilOk = soilState.is(BlockTags.DIRT) || soilState.is(Blocks.FARMLAND)
                || soilState.is(Blocks.GRASS_BLOCK) || soilState.is(Blocks.MUD);
        cir.setReturnValue(cellFree && soilOk);
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapeBushPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                               BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!GrapeBushHelper.isGrapeBush(crop)) return;
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        ensureSoilBelow(level, data.boxPos(), cropPos, chestPositions);
        level.setBlock(cropPos, crop.plantState(), 3);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapeBushHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                          CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!GrapeBushHelper.isGrapeBush(crop)) return;
        BlockState state = level.getBlockState(cropPos);
        cir.setReturnValue(state.is(crop.plantBlock()) && GrapeBushHelper.isMature(state));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapeBushHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                  BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!GrapeBushHelper.isGrapeBush(crop)) return;
        BlockState state = level.getBlockState(cropPos);
        if (!state.is(crop.plantBlock()) || !GrapeBushHelper.isMature(state)) return;
        List<net.minecraft.world.item.ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, level, cropPos, level.getBlockEntity(cropPos));
        GrapeBushHelper.harvest(level, cropPos, state);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsWaterWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapevineSkipWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                   BlockPos cropPos, CallbackInfoReturnable<Boolean> cir) {
        if (GrapevineCropHelper.isGrapevineCrop(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsTillWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapevineCropSkipTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (GrapevineCropHelper.isGrapevineCrop(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapevineCropPlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                            CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!GrapevineCropHelper.isGrapevineCrop(crop)) return;
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(GrapevineCropHelper.needsPlant(level, cropPos, crop));
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapevineCropPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                   BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!GrapevineCropHelper.isGrapevineCrop(crop)) return;
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        ensureSoilBelow(level, data.boxPos(), cropPos, chestPositions);
        GrapevineCropHelper.plant(level, cropPos, crop, data.plot());
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapevineCropHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                              CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!GrapevineCropHelper.isGrapevineCrop(crop)) return;
        cir.setReturnValue(GrapevineCropHelper.isMature(level, cropPos, crop));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$grapevineCropHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                     BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!GrapevineCropHelper.isGrapevineCrop(crop)) return;
        if (!GrapevineCropHelper.isMature(level, cropPos, crop)) return;
        BlockPos harvestPos = cropPos.above();
        BlockState state = GrapevineCropHelper.getCropState(level, cropPos);
        List<net.minecraft.world.item.ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, level, harvestPos, level.getBlockEntity(harvestPos));
        GrapevineCropHelper.harvest(level, cropPos);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, harvestPos);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), remap = false)
    private static void xy2407$ensureSoilBeforePlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                      BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (RiceCropHelper.isRiceCrop(crop) || GrapeBushHelper.isGrapeBush(crop) || GrapevineCropHelper.isGrapevineCrop(crop)) {
            return;
        }
        ensureSoilBelow(level, data.boxPos(), cropPos, chestPositions);
    }

    private static void ensureSoilBelow(ServerLevel level, BlockPos boxPos, BlockPos soilCheckPos, List<BlockPos> chestPositions) {
        BlockState soilState = level.getBlockState(soilCheckPos);
        if (isSoilForCrops(soilState)) {
            return;
        }
        if (soilState.isAir()) {
            level.setBlock(soilCheckPos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if (isProtectedBlock(level, boxPos, soilCheckPos, soilState, chestPositions)) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(soilState, level, soilCheckPos, level.getBlockEntity(soilCheckPos));
        level.setBlock(soilCheckPos, Blocks.DIRT.defaultBlockState(), 3);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, soilCheckPos);
    }

    private static boolean isSoilForCrops(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MUD);
    }

    private static boolean isProtectedBlock(ServerLevel level, BlockPos boxPos, BlockPos pos, BlockState state, List<BlockPos> chestPositions) {
        if (pos.equals(boxPos) || chestPositions.contains(pos)) {
            return true;
        }
        if (state.is(Blocks.BEDROCK)) {
            return true;
        }
        if (state.is(common.cn.kafei.simukraft.registry.ModBlocks.NSUK_FARMLAND_BOX.get())
                || state.is(common.cn.kafei.simukraft.registry.ModBlocks.BUILD_BOX.get())
                || state.is(common.cn.kafei.simukraft.registry.ModBlocks.CITY_CORE.get())) {
            return true;
        }
        return GenericContainerAccess.isContainer(level, pos);
    }
}
