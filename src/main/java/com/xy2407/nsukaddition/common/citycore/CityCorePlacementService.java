package com.xy2407.nsukaddition.common.citycore;

import common.cn.kafei.simukraft.building.BuildingBlockPlacementService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** 城市核心建筑放置服务：按图纸旋转值在投影位置放置解析后的 citycore.nbt 建筑。 */
public final class CityCorePlacementService {

    private CityCorePlacementService() {
    }

    public static void place(ServerLevel level, Player player, ItemStack stack) {
        if (level == null || player == null) {
            return;
        }
        int rotation = CityCoreRotationUtil.getRotation(stack);
        CityCoreStructure structure = CityCoreNbtLoader.get().rotated(rotation);
        if (structure == null || structure.isEmpty()) {
            return;
        }

        BlockPos projection = CityCoreProjectionUtil.projectionPos(player);
        BlockPos anchor = projection.subtract(structure.origin());
        List<BlockPos> corePositions = new ArrayList<>();
        for (CityCoreStructure.CityCoreBlock block : structure.blocks()) {
            BlockPos worldPos = anchor.offset(block.pos());
            if (worldPos.getY() < level.getMinBuildHeight() || worldPos.getY() > level.getMaxBuildHeight() - 1) {
                continue;
            }
            if (!level.isLoaded(worldPos)) {
                continue;
            }
            BlockState state = BuildingBlockPlacementService.refreshedPlacementState(level, worldPos, block.state());
            level.setBlock(worldPos, state, 3);
            BuildingBlockPlacementService.applyBlockEntityData(level, worldPos, block.blockEntityData());
            if (state.is(ModBlocks.CITY_CORE.get())) {
                corePositions.add(worldPos.immutable());
            }
        }
        for (BlockPos corePos : corePositions) {
            VillageCityConversionTrigger.onCorePlaced(level, corePos);
        }
    }
}