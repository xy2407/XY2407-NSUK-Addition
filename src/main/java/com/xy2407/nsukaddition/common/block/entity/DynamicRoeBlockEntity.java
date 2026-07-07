package com.xy2407.nsukaddition.common.block.entity;

import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 动态鱼卵方块实体，存储父鱼类型信息用于孵化时生成对应鱼类实体。 */
public class DynamicRoeBlockEntity extends BlockEntity {

    private String fishType = null;

    public DynamicRoeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DYNAMIC_ROE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setFishType(String fishType) {
        this.fishType = fishType;
        setChanged();
    }

    public String getFishType() {
        return fishType;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (fishType != null) {
            tag.putString("FishType", fishType);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FishType")) {
            fishType = tag.getString("FishType");
        }
    }
}
