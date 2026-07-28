package com.xy2407.nsukaddition.mixin.tide;

import com.teammetallurgy.aquaculture.api.AquacultureAPI;
import com.li64.tide.registries.items.FishSatchelItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修改 FishSatchelItem，扩展鱼篓容量至五百一十二组并兼容水产养殖鱼类。 */
@Mixin(FishSatchelItem.class)
public class FishSatchelItemMixin {

    private static final int NEW_MAX_STACKS = 512;

    @Inject(method = "canPutInSatchel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onCanPutInSatchel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            try {
                if (AquacultureAPI.FISH_DATA.getFish().contains(stack.getItem())) {
                    cir.setReturnValue(true);
                }
            } catch (Exception ignored) {

            }
        }
    }

    @ModifyConstant(method = "getRemainingSlots", constant = @Constant(intValue = 64), remap = false)
    private static int modifyMaxStacksRemaining(int original) {
        return NEW_MAX_STACKS;
    }

    @ModifyConstant(method = "getBarWidth", constant = @Constant(intValue = 64), remap = false)
    private int modifyMaxStacksBar(int original) {
        return NEW_MAX_STACKS;
    }

    @ModifyConstant(method = "addTooltip", constant = @Constant(intValue = 64), remap = false)
    private int modifyMaxStacksTooltip(int original) {
        return NEW_MAX_STACKS;
    }
}
