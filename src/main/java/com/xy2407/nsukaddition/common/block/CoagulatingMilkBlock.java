package com.xy2407.nsukaddition.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** 奶流体方块：放置后 1200 tick 凝固为对应奶酪方块（奶酪方块按 ID 运行时解析，无硬依赖）。 */
public class CoagulatingMilkBlock extends LiquidBlock {
    public static final int COAGULATION_TICKS = 1200;
    private final ResourceLocation cheeseBlockId;

    public CoagulatingMilkBlock(FlowingFluid fluid, Properties properties, ResourceLocation cheeseBlockId) {
        super(fluid, properties);
        this.cheeseBlockId = cheeseBlockId;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, COAGULATION_TICKS);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (state.is(this) && state.getFluidState().isSource()) {
            level.scheduleTick(pos, this, COAGULATION_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (state.is(this) && state.getFluidState().isSource()) {
            Block cheese = resolveCheese();
            if (cheese != null) {
                level.setBlockAndUpdate(pos, cheese.defaultBlockState());
            }
        }
    }

    private Block resolveCheese() {
        Block cheese = BuiltInRegistries.BLOCK.get(cheeseBlockId);
        if (cheese == Blocks.AIR) {
            cheese = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simukraft", "cheese_block"));
        }
        return cheese == Blocks.AIR ? null : cheese;
    }
}
