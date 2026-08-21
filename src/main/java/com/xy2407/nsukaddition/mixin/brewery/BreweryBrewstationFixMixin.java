package com.xy2407.nsukaddition.mixin.brewery;

import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.brewery.core.block.BrewKettleBlock;
import net.satisfy.brewery.core.block.entity.BrewstationBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * SimuKraft 建筑系统通过 level.setBlock() 逐个放置方块，不经过 BlockItem.useOn -> setPlacedBy，
 * 导致 Brewery 酿造站(BrewKettleBlock)周围 3 个组件方块(烤炉/计时器/哨子)未放置、
 * BrewstationBlockEntity.components 未装配，tick 时 canBrew 的 getBlockState(null) NPE 崩溃。
 * 此 Mixin 在建筑完成后扫描其中的 BrewKettleBlock 原点，补齐组件方块并装配 components。
 */
@Mixin(BuilderConstructionService.class)
public class BreweryBrewstationFixMixin {

    @ModifyArgs(
            method = "completeTask",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/building/PlacedBuildingService;register(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/building/PlacedBuildingRecord;)V"),
            remap = false
    )
    private static void xy2407$fixBreweryBrewstation(Args args) {
        ServerLevel level = args.get(0);
        PlacedBuildingRecord placedBuilding = args.get(1);
        if (level == null || placedBuilding == null) return;
        fixBrewstationsInBuilding(level, placedBuilding);
    }

    private static void fixBrewstationsInBuilding(ServerLevel level, PlacedBuildingRecord placedBuilding) {
        var blocks = placedBuilding.blocks();
        if (blocks.isEmpty()) return;

        for (var block : blocks) {
            BlockState state = block.state();
            if (!(state.getBlock() instanceof BrewKettleBlock)) continue;

            BlockPos origin = block.relativePos().immutable();
            assembleKettle(level, origin, state);
        }
    }

    private static void assembleKettle(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BrewKettleBlock)) return;

        Direction facing = state.getValue(BrewKettleBlock.FACING);
        BlockPos backPos = pos.relative(facing.getOpposite());
        BlockPos sidePos = pos.relative(facing.getCounterClockWise());
        BlockPos diagonalPos = sidePos.relative(facing.getOpposite());

        placeComponentIfAir(level, backPos, state, getBreweryBlock("brew_timer"));
        placeComponentIfAir(level, sidePos, state, getBreweryBlock("brew_whistle"));
        placeComponentIfAir(level, diagonalPos, state, getBreweryBlock("brew_oven"));

        if (level.getBlockEntity(pos) instanceof BrewstationBlockEntity kettle && kettle.getComponents().isEmpty()) {
            kettle.setComponents(pos, backPos, sidePos, diagonalPos);
        }
    }

    private static Block getBreweryBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("brewery", path));
    }

    private static void placeComponentIfAir(ServerLevel level, BlockPos pos, BlockState kettleState,
                                            Block componentBlock) {
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, componentBlock.defaultBlockState()
                .setValue(BrewKettleBlock.FACING, kettleState.getValue(BrewKettleBlock.FACING))
                .setValue(net.satisfy.brewery.core.registry.BlockStateRegistry.MATERIAL,
                        kettleState.getValue(net.satisfy.brewery.core.registry.BlockStateRegistry.MATERIAL)), 3);
    }
}
