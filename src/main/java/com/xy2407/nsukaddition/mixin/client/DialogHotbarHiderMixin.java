package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.DialogScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 对话框打开期间隐藏快捷栏(HUD)。 */
@Mixin(Gui.class)
public abstract class DialogHotbarHiderMixin {

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void nsuk$hideHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (DialogScreen.isOpen()) {
            ci.cancel();
        }
    }
}