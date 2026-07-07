package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 扩展农田种植/收获逻辑，支持水稻、葡萄等特殊作物。 */
@Mixin(FarmlandFarmingService.class)
public class FarmlandFarmingServiceMixin {

    @Inject(method = "needsWaterWork", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$riceNeedsWaterWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions,
                                                   BlockPos cropPos, CallbackInfoReturnable<Boolean> cir) {
        FarmCrop crop = data.crop();
        if (!RiceCropHelper.isRiceCrop(crop)) {
            return;
        }
        if (!crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            cir.setReturnValue(false);
            return;
        }
        BlockState belowState = level.getBlockState(cropPos.below());

        if (belowState.is(crop.plantBlock())) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(!belowState.is(Blocks.WATER));
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

        BlockState soilState = level.getBlockState(plantPos.below());
        if (!soilState.is(net.minecraft.tags.BlockTags.DIRT) && !soilState.is(Blocks.FARMLAND) && !soilState.is(Blocks.GRASS_BLOCK)) {
            level.setBlock(plantPos.below(), Blocks.DIRT.defaultBlockState(), 3);
        }

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
}
