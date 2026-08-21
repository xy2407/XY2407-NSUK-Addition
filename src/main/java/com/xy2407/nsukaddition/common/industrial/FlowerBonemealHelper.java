package com.xy2407.nsukaddition.common.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 花方块骨粉掉落核心：供骨粉 Mixin 与花棚 NPC 工作逻辑共用。
 */
public final class FlowerBonemealHelper {

    private FlowerBonemealHelper() {
    }

    public static boolean bonemealFlowerDrop(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        Item flowerItem = state.getBlock().asItem();
        if (flowerItem != Items.AIR) {
            Block.popResource(level, pos, new ItemStack(flowerItem));
        }
        return true;
    }
}