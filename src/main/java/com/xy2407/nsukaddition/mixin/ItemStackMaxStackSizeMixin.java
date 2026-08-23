package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.registry.ModMilkFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 奶桶可堆叠16个，金币可堆叠99个(原版codec硬上限)。 */
@Mixin(ItemStack.class)
public abstract class ItemStackMaxStackSizeMixin {

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$customStackSize(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = ((ItemStack) (Object) this);
        if (stack.is(Items.MILK_BUCKET) || ModMilkFluids.milkTypeByBucket(stack) != null
                || stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kawaiidishes:steamed_milk_bucket")))
                || stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kawaiidishes:milk_foam_bucket")))) {
            cir.setReturnValue(16);
        } else if (stack.is(common.cn.kafei.simukraft.registry.ModItems.GOLD_COIN.get())) {
            cir.setReturnValue(99);
        }
    }
}
