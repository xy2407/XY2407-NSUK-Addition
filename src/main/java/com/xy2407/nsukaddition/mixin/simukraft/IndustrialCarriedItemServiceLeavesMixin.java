package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.industrial.IndustrialCarriedItemService;
import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 工业 deposit 入箱时放行树叶物品，使伐木场采到的树叶能存进箱子（不受 itemSpecs 白名单过滤）。 */
@Mixin(IndustrialCarriedItemService.class)
public abstract class IndustrialCarriedItemServiceLeavesMixin {

    @Redirect(
            method = "lambda$depositToContainers$2(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/HolderLookup$Provider;Lcommon/cn/kafei/simukraft/industrial/IndustrialItemStackSpec;)Z",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/industrial/IndustrialItemStackSpec;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/HolderLookup$Provider;)Z"),
            remap = false
    )
    private static boolean nsukaddition$allowLeafDeposit(IndustrialItemStackSpec spec, ItemStack stack, HolderLookup.Provider registries) {
        if (spec.matches(stack, registries)) {
            return true;
        }
        if (stack != null && !stack.isEmpty()) {
            Item item = stack.getItem();
            if (item instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState().is(BlockTags.LEAVES);
            }
        }
        return false;
    }
}
