package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.hud.SidebarHudLayer;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 MouseHandler，侧边栏 HUD 开启时拦截滚轮和右键输入。 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!SidebarHudLayer.isVisible()) return;

        ci.cancel();

        int delta = vertical > 0 ? -1 : 1;
        SidebarHudLayer.cycleSelection(delta);
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$onPress(long window, int button, int action, int mods, CallbackInfo ci) {

        if (button != 1 || action != 1) return;
        if (!SidebarHudLayer.isVisible()) return;
        if (SidebarHudLayer.getSelectedIndex() < 0) return;

        ci.cancel();

        SidebarHudLayer.executeSelectedButton();
    }
}
