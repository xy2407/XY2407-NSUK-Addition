package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.WorkContainerService;

import com.xy2407.nsukaddition.common.farmland.BerryCropHelper;
import com.xy2407.nsukaddition.common.farmland.FarmlandWorkResultAccess;
import com.xy2407.nsukaddition.common.farmland.GlowBerryHelper;
import com.xy2407.nsukaddition.common.farmland.GrapeBushHelper;
import com.xy2407.nsukaddition.common.farmland.GrapevineCropHelper;
import com.xy2407.nsukaddition.common.farmland.HopsCropHelper;
import com.xy2407.nsukaddition.common.farmland.JungleVineCropHelper;
import com.xy2407.nsukaddition.common.farmland.MushroomCropHelper;
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
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
    private static void xy2407$riceSkipWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
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
        ensureSoilBelow(level, data.boxPos(), cropPos.below(), chestPositions);
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
        ensureSoilBelow(level, data.boxPos(), cropPos.below(), chestPositions);
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

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$berryPlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                     CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!BerryCropHelper.isBerryBush(crop)) {
            return;
        }
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(BerryCropHelper.needsPlant(level, cropPos, crop));
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$berryPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                           BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!BerryCropHelper.isBerryBush(crop)) {
            return;
        }
        if (!BerryCropHelper.plant(level, chestPositions, crop, cropPos)) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$berryHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                       CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!BerryCropHelper.isBerryBush(crop)) {
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        cir.setReturnValue(crop.isOwnPlant(state) && BerryCropHelper.isMature(state));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$berryHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                             BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!BerryCropHelper.isBerryBush(crop)) {
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isOwnPlant(state) || !BerryCropHelper.isMature(state)) {
            return;
        }
        BerryCropHelper.harvest(level, chestPositions, crop, cropPos, state);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$hopsPlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                    CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!HopsCropHelper.isHopsCrop(crop)) {
            return;
        }
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(HopsCropHelper.needsPlant(level, cropPos, crop));
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$hopsPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                          BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!HopsCropHelper.isHopsCrop(crop)) {
            return;
        }
        if (!HopsCropHelper.plant(level, chestPositions, crop, cropPos)) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$hopsHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                      CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!HopsCropHelper.isHopsCrop(crop)) {
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        cir.setReturnValue(crop.isOwnPlant(state) && HopsCropHelper.isMature(state));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$hopsHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                            BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!HopsCropHelper.isHopsCrop(crop)) {
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isOwnPlant(state) || !HopsCropHelper.isMature(state)) {
            return;
        }
        HopsCropHelper.harvest(level, chestPositions, cropPos, state);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsWaterWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$jungleSkipWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                BlockPos cropPos, CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (JungleVineCropHelper.isJungleGrape(crop) || JungleVineCropHelper.isCocoa(crop) || GlowBerryHelper.isGlowBerry(crop)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsTillWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$jungleSkipTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                               CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (JungleVineCropHelper.isJungleGrape(crop) || JungleVineCropHelper.isCocoa(crop) || GlowBerryHelper.isGlowBerry(crop)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$junglePlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                      CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (JungleVineCropHelper.isJungleGrape(crop) || JungleVineCropHelper.isCocoa(crop)) {
            ciPlantReturn(crop, cropPos, JungleVineCropHelper.needsSidePlant(data.plot(), level, cropPos), cir);
        }
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$junglePlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                            BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!JungleVineCropHelper.isJungleGrape(crop) && !JungleVineCropHelper.isCocoa(crop)) {
            return;
        }
        if (JungleVineCropHelper.anyMissingSupport(level, data.plot())) {
            boolean ok = JungleVineCropHelper.plantSupportsOnly(level, chestPositions, cropPos);
            if (!ok) {
                MISSING_JUNGLE_LOG.set(true);
                cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            } else {
                DOING_JUNGLE.set(true);
                cir.setReturnValue(FarmlandWorkResultAccess.processed());
            }
            return;
        }
        boolean ok = JungleVineCropHelper.plantSide(level, chestPositions, crop, cropPos);
        cir.setReturnValue(ok ? FarmlandWorkResultAccess.processed() : FarmlandWorkResultAccess.waitingSeed());
    }

    private static final java.util.concurrent.atomic.AtomicBoolean MISSING_JUNGLE_LOG =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final String MISSING_JUNGLE_LOG_KEY =
            "gui.simukraft.farmland.status.missing_jungle_log";

    private static final java.util.concurrent.atomic.AtomicBoolean MISSING_RICH_SOIL =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String MISSING_RICH_SOIL_KEY =
            "gui.simukraft.farmland.status.missing_rich_soil";

    private static final java.util.concurrent.atomic.AtomicBoolean DOING_JUNGLE =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean DOING_RICH =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String DOING_JUNGLE_LOG_KEY =
            "gui.simukraft.farmland.status.doing_jungle_log";
    private static final String DOING_RICH_SOIL_KEY =
            "gui.simukraft.farmland.status.doing_rich_soil";

    @ModifyArg(method = "tickBox", at = @At(value = "INVOKE", target = "farmerStatusLabel", ordinal = 2), index = 1, remap = false)
    private static String nsuk$fixDoingSpecialMaterialKey(String translationKey) {
        if (DOING_JUNGLE.getAndSet(false)) {
            return DOING_JUNGLE_LOG_KEY;
        }
        if (DOING_RICH.getAndSet(false)) {
            return DOING_RICH_SOIL_KEY;
        }
        return translationKey;
    }

    @ModifyArg(method = "tickBox", at = @At(value = "INVOKE", target = "farmerStatusLabel", ordinal = 3), index = 1, remap = false)
    private static String nsuk$fixMissingSpecialMaterialKey(String translationKey) {
        if (MISSING_RICH_SOIL.getAndSet(false)) {
            return MISSING_RICH_SOIL_KEY;
        }
        if (MISSING_JUNGLE_LOG.getAndSet(false)) {
            return MISSING_JUNGLE_LOG_KEY;
        }
        return translationKey;
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$jungleHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                        CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (JungleVineCropHelper.isJungleGrape(crop)) {
            cir.setReturnValue(JungleVineCropHelper.isJungleGrapeMature(level, cropPos, crop));
            return;
        }
        if (JungleVineCropHelper.isCocoa(crop)) {
            cir.setReturnValue(JungleVineCropHelper.isCocoaMature(level, cropPos, crop));
        }
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$jungleHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                              BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (JungleVineCropHelper.isJungleGrape(crop)) {
            if (!JungleVineCropHelper.isJungleGrapeMature(level, cropPos, crop)) {
                return;
            }
            JungleVineCropHelper.harvestJungleGrape(level, chestPositions, crop, cropPos, level.getBlockState(cropPos));
            cir.setReturnValue(FarmlandWorkResultAccess.processed());
            return;
        }
        if (JungleVineCropHelper.isCocoa(crop)) {
            if (!JungleVineCropHelper.isCocoaMature(level, cropPos, crop)) {
                return;
            }
            JungleVineCropHelper.harvestCocoa(level, chestPositions, crop, cropPos, level.getBlockState(cropPos));
            cir.setReturnValue(FarmlandWorkResultAccess.processed());
        }
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$glowPlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                    CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!GlowBerryHelper.isGlowBerry(crop)) {
            return;
        }
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(GlowBerryHelper.needsPlant(level, cropPos, crop));
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$glowPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                          BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!GlowBerryHelper.isGlowBerry(crop)) {
            return;
        }
        boolean willPlaceLog = GlowBerryHelper.needsLog(level, cropPos);
        if (willPlaceLog && !GlowBerryHelper.hasLog(level, chestPositions)) {
            MISSING_JUNGLE_LOG.set(true);
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        if (!GlowBerryHelper.plant(level, chestPositions, crop, cropPos)) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        if (willPlaceLog) {
            DOING_JUNGLE.set(true);
        }
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$glowHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                      CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!GlowBerryHelper.isGlowBerry(crop)) {
            return;
        }
        cir.setReturnValue(GlowBerryHelper.isMature(level, cropPos, crop));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$glowHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                            BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!GlowBerryHelper.isGlowBerry(crop)) {
            return;
        }
        if (!GlowBerryHelper.isMature(level, cropPos, crop)) {
            return;
        }
        GlowBerryHelper.harvest(level, chestPositions, crop, cropPos, level.getBlockState(cropPos));
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    private static void ciPlantReturn(FarmCrop crop, BlockPos cropPos, boolean value, CallbackInfoReturnable<Boolean> cir) {
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(value);
    }

    @Inject(method = "needsWaterWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$mushroomSkipWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                  BlockPos cropPos, CallbackInfoReturnable<Boolean> cir) {
        if (MushroomCropHelper.isMushroomCrop(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsTillWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$mushroomSkipTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (MushroomCropHelper.isMushroomCrop(data.crop())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$mushroomPlantCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                       CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!MushroomCropHelper.isMushroomCrop(crop)) {
            return;
        }
        cir.setReturnValue(!MushroomCropHelper.hasAnyCrop(level, cropPos));
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$mushroomPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                              BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!MushroomCropHelper.isMushroomCrop(crop)) {
            return;
        }
        if (MushroomCropHelper.anyMissingSoil(level, data.plot(), data.boxPos())) {
            boolean ok = MushroomCropHelper.placeSoilOnly(level, chestPositions, cropPos);
            if (!ok) {
                MISSING_RICH_SOIL.set(true);
            } else {
                DOING_RICH.set(true);
            }
            cir.setReturnValue(ok ? FarmlandWorkResultAccess.processed() : FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        if (MushroomCropHelper.hasAnyCrop(level, cropPos)) {
            cir.setReturnValue(FarmlandWorkResultAccess.processed());
            return;
        }
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            cir.setReturnValue(FarmlandWorkResultAccess.waitingSeed());
            return;
        }
        level.setBlock(cropPos, crop.plantState(), 3);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$mushroomHarvestCondition(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                         CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!MushroomCropHelper.isMushroomCrop(crop)) {
            return;
        }
        cir.setReturnValue(MushroomCropHelper.isColonyHarvestable(level, crop, cropPos));
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$mushroomHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (!MushroomCropHelper.isMushroomCrop(crop)) {
            return;
        }
        MushroomCropHelper.harvestColony(level, chestPositions, crop, cropPos);
        cir.setReturnValue(FarmlandWorkResultAccess.processed());
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), remap = false)
    private static void xy2407$ensureSoilBeforePlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                      BlockPos cropPos, CallbackInfoReturnable<?> cir) {
        FarmCrop crop = data.crop();
        if (RiceCropHelper.isRiceCrop(crop) || GrapeBushHelper.isGrapeBush(crop) || GrapevineCropHelper.isGrapevineCrop(crop)) {
            return;
        }
        if (JungleVineCropHelper.isJungleGrape(crop) || JungleVineCropHelper.isCocoa(crop) || GlowBerryHelper.isGlowBerry(crop)) {
            return;
        }
        if (MushroomCropHelper.isMushroomCrop(crop)) {
            return;
        }
        ensureSoilBelow(level, data.boxPos(), cropPos.below(), chestPositions);
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
