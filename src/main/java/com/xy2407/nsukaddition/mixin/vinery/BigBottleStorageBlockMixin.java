package com.xy2407.nsukaddition.mixin.vinery;

import com.xy2407.nsukaddition.common.compat.vinerykaleidoscope.VineryKaleidoscopeCompat;
import net.minecraft.world.item.ItemStack;
import net.satisfy.vinery.core.block.BigBottleStorageBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Vinery 大瓶存储架额外接受 Kaleidoscope 酒瓶。 */
@Mixin(BigBottleStorageBlock.class)
public class BigBottleStorageBlockMixin {

    @Inject(method = "canInsertStack", at = @At("RETURN"), cancellable = true, remap = false)
    private void nsuk$acceptRegularKaleidoscopeBottle(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && VineryKaleidoscopeCompat.isRegularKaleidoscopeBottle(stack)) {
            cir.setReturnValue(true);
        }
    }
}
