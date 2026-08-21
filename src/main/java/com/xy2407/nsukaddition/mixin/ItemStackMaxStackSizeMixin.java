package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.registry.ModMilkFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 奶桶（原版 + 七种木奶桶）与空桶一致可堆叠 16 个。 */
@Mixin(ItemStack.class)
public abstract class ItemStackMaxStackSizeMixin {

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$milkBucketStack16(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = ((ItemStack) (Object) this);
        if (stack.is(Items.MILK_BUCKET) || ModMilkFluids.milkTypeByBucket(stack) != null) {
            cir.setReturnValue(16);
        }
    }
}
