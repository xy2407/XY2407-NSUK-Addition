package com.xy2407.nsukaddition.mixin;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让金币在任意容器/背包槽位的堆叠上限也变为 999。 */
@Mixin(Slot.class)
public abstract class SlotMaxStackSizeMixin {

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$coinSlotStackSize(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack != null && stack.is(common.cn.kafei.simukraft.registry.ModItems.GOLD_COIN.get())) {
            cir.setReturnValue(99);
        }
    }

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$coinSlotStackSizePlain(CallbackInfoReturnable<Integer> cir) {
        Slot slot = ((Slot) (Object) this);
        if (slot.getItem().is(common.cn.kafei.simukraft.registry.ModItems.GOLD_COIN.get())) {
            cir.setReturnValue(99);
        }
    }
}