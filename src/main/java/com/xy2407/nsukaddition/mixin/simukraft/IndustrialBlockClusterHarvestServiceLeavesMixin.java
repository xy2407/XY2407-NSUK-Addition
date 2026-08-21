package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.industrial.IndustrialBlockClusterHarvestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** 伐木砍树时让树叶掉落树叶物品（原版非剪刀不掉落），供 NPC 收集入箱。 */
@Mixin(IndustrialBlockClusterHarvestService.class)
public abstract class IndustrialBlockClusterHarvestServiceLeavesMixin {

    @Redirect(
            method = "fellActiveCluster",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"),
            remap = false
    )
    private static List<ItemStack> nsukaddition$harvestLeafDrops(BlockState state, ServerLevel level, BlockPos pos,
                                                                  BlockEntity blockEntity, Entity entity, ItemStack tool) {
        if (state.is(BlockTags.LEAVES)) {
            Item leafItem = state.getBlock().asItem();
            if (leafItem != Items.AIR) {
                return List.of(new ItemStack(leafItem));
            }
        }
        return Block.getDrops(state, level, pos, blockEntity, entity, tool);
    }
}
