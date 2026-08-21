package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 模式下隐藏原版 HUD 元素：物品栏、血条、经验条、状态效果、准星。 */
@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$hideHotbar(GuiGraphics gg, DeltaTracker dt, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$hideExpLevel(GuiGraphics gg, DeltaTracker dt, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$hideEffects(GuiGraphics gg, DeltaTracker dt, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$hideCrosshair(GuiGraphics gg, DeltaTracker dt, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            ci.cancel();
        }
    }
}
