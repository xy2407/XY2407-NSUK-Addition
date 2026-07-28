package com.xy2407.nsukaddition.common.breeding;

import com.chinaex123.letfishlove.capabilities.FishBreedingCapAttacher;
import com.chinaex123.letfishlove.entity.FishBreedingUtil;
import com.xy2407.nsukaddition.common.block.DynamicRoeBlock;
import com.xy2407.nsukaddition.common.block.entity.DynamicRoeBlockEntity;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.EnumSet;

/** 岩浆鱼产卵 AI 目标，怀孕鱼寻找岩浆放置动态鱼卵方块。 */
public class LavaFishLayRoeGoal extends MoveToBlockGoal {

    private final PathfinderMob fish;

    public LavaFishLayRoeGoal(PathfinderMob fish) {
        super(fish, 0.8F, 10, 5);
        this.fish = fish;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        var cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
        return cap != null && cap.isPregnant() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.fish.getNavigation().isDone()) return false;
        var cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
        return cap != null && cap.isPregnant() && super.canContinueToUse();
    }

    @Override
    public double acceptedDistance() {
        return 0.0D;
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos).getFluidState().is(Fluids.LAVA);
    }

    @Override
    public void stop() {
        Level level = this.fish.level();
        BlockPos fishPos = this.getMoveToTarget();
        var cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
        if (cap == null || !cap.isPregnant()) return;

        String fishTypeName = BuiltInRegistries.ENTITY_TYPE.getKey(fish.getType()).toString();

        boolean hasLava = level.getFluidState(fishPos).getType() == Fluids.LAVA;
        BlockState roeState = ModBlocks.DYNAMIC_ROE_BLOCK.get().defaultBlockState()
                .setValue(DynamicRoeBlock.WATERLOGGED, hasLava);

        boolean placed = level.setBlockAndUpdate(fishPos, roeState);
        if (placed && level.getBlockEntity(fishPos) instanceof DynamicRoeBlockEntity blockEntity) {
            blockEntity.setFishType(fishTypeName);
        }

        cap.setPregnant(false, true);
    }
}
