package com.xy2407.nsukaddition.common.citycore;

import common.cn.kafei.simukraft.building.BuildingTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** 城市核心建筑结构数据，由 citycore.nbt 解析得到，origin 为结构最小角。 */
public record CityCoreStructure(List<CityCoreBlock> blocks, BlockPos origin) {

    public static final CityCoreStructure EMPTY = new CityCoreStructure(List.of(), BlockPos.ZERO);

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public CityCoreStructure rotated(int rotationDegrees) {
        int rotation = Math.floorMod(rotationDegrees, 360);
        if (rotation == 0 || isEmpty()) {
            return this;
        }
        List<CityCoreBlock> rotatedBlocks = new ArrayList<>(blocks.size());
        for (CityCoreBlock block : blocks) {
            BlockPos relative = block.pos().subtract(origin);
            BlockPos rotatedPos = BuildingTransform.rotatePosition(relative, rotation).offset(origin);
            BlockState rotatedState = BuildingTransform.rotateState(block.state(), rotation);
            rotatedBlocks.add(new CityCoreBlock(rotatedPos, rotatedState, block.copyBlockEntityData()));
        }
        return new CityCoreStructure(List.copyOf(rotatedBlocks), computeOrigin(rotatedBlocks));
    }

    public static BlockPos computeOrigin(List<CityCoreBlock> blocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (CityCoreBlock block : blocks) {
            minX = Math.min(minX, block.pos().getX());
            minY = Math.min(minY, block.pos().getY());
            minZ = Math.min(minZ, block.pos().getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return BlockPos.ZERO;
        }
        return new BlockPos(minX, minY, minZ);
    }

    public record CityCoreBlock(BlockPos pos, BlockState state, CompoundTag blockEntityData) {
        public CityCoreBlock {
            blockEntityData = blockEntityData != null ? blockEntityData.copy() : null;
        }

        public CompoundTag copyBlockEntityData() {
            return blockEntityData != null ? blockEntityData.copy() : null;
        }
    }
}
