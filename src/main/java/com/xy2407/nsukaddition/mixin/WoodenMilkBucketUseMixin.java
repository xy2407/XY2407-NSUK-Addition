package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.fluid.WoodenMilkBucketPlacementService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 木奶桶右键放置对应奶流体：按物品 ID 命中 7 种木奶桶，其余（含原版奶桶）交还原逻辑。 */
@Mixin(MilkBucketItem.class)
public abstract class WoodenMilkBucketUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$pourWoodenMilk(Level level, Player player, InteractionHand hand,
                                             CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        InteractionResultHolder<ItemStack> result = WoodenMilkBucketPlacementService.tryPourWoodenMilk(level, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
