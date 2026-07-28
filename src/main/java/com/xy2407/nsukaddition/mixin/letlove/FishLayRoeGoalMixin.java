package com.xy2407.nsukaddition.mixin.letlove;

import com.chinaex123.letfishlove.entity.FishBreedingUtil;
import com.chinaex123.letfishlove.entity.FishLayRoeGoal;
import com.xy2407.nsukaddition.common.block.DynamicRoeBlock;
import com.xy2407.nsukaddition.common.block.entity.DynamicRoeBlockEntity;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** 修改 FishLayRoeGoal，重写产卵结束逻辑以放置动态鱼卵方块。 */
@Mixin(FishLayRoeGoal.class)
public abstract class FishLayRoeGoalMixin {

    @Shadow
    @Final
    private WaterAnimal fish;

    @Overwrite
    public void stop() {
        var level = this.fish.level();
        BlockPos fishPos = BlockPos.containing(this.fish.position());

        if (!FishBreedingUtil.getFishCap(fish).isPregnant()) {
            return;
        }

        String fishTypeName = BuiltInRegistries.ENTITY_TYPE.getKey(fish.getType()).toString();

        boolean hasWater = level.getFluidState(fishPos).getType() == Fluids.WATER;
        BlockState roeState = ModBlocks.DYNAMIC_ROE_BLOCK.get().defaultBlockState()
                .setValue(DynamicRoeBlock.WATERLOGGED, hasWater);

        boolean placed = level.setBlockAndUpdate(fishPos, roeState);
        if (placed) {
            if (level.getBlockEntity(fishPos) instanceof DynamicRoeBlockEntity blockEntity) {
                blockEntity.setFishType(fishTypeName);
            }
        }

        FishBreedingUtil.getFishCap(fish).setPregnant(false, true);
    }
}
