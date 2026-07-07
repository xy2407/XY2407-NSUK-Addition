package com.xy2407.nsukaddition.common.farmland;

import common.cn.kafei.simukraft.farmland.FarmCrop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Set;

/** 处理葡萄灌木类作物的成熟判断与采摘后年龄重置。 */
public final class GrapeBushHelper {

    private static final Set<String> GRAPE_BUSH_IDS = Set.of(
            "vinery:red_grape_bush",
            "vinery:white_grape_bush",
            "vinery:savanna_grape_bush_red",
            "vinery:savanna_grape_bush_white",
            "vinery:taiga_grape_bush_red",
            "vinery:taiga_grape_bush_white"
    );

    public static final int HARVEST_RESET_AGE = 1;

    public static final int MATURE_AGE = 2;

    private GrapeBushHelper() {}

    public static boolean isGrapeBush(FarmCrop crop) {
        if (crop == null || crop.plantBlock() == null) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(crop.plantBlock());
        return id != null && GRAPE_BUSH_IDS.contains(id.toString());
    }

    public static boolean isGrapeBushId(ResourceLocation blockId) {
        return blockId != null && GRAPE_BUSH_IDS.contains(blockId.toString());
    }

    public static boolean isMature(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.AGE_3)) return false;
        return state.getValue(BlockStateProperties.AGE_3) >= MATURE_AGE;
    }

    public static BlockState harvest(ServerLevel level, BlockPos pos, BlockState state) {
        IntegerProperty ageProp = BlockStateProperties.AGE_3;
        BlockState resetState = state.setValue(ageProp, HARVEST_RESET_AGE);
        level.setBlock(pos, resetState, Block.UPDATE_CLIENTS);
        return resetState;
    }
}
