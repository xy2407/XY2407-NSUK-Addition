package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.fluid.MilkBucketPlacementService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** simukraft 奶桶倒奶逻辑只对原版牛奶桶生效，避免误处理继承 MilkBucketItem 的 mod 物品。 */
@Mixin(MilkBucketPlacementService.class)
public abstract class MilkBucketPlacementServiceMixin {

    @Inject(method = "tryPourMilk", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsukaddition$vanillaMilkBucketOnly(Level level, Player player, InteractionHand hand,
                                                           CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack == null || !stack.is(Items.MILK_BUCKET)) {
            cir.setReturnValue(null);
        }
    }
}