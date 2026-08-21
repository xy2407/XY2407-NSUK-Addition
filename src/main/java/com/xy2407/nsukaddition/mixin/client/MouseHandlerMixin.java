package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.hud.SidebarHudLayer;
import com.xy2407.nsukaddition.client.rts.RtsInputHandler;
import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 MouseHandler：RTS 模式下拦截所有鼠标输入（左/右/中键+滚轮），侧边栏 HUD 处理右键，打开 GUI 时放行。 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) return;

        if (RtsModeManager.isActive()) {
            ci.cancel();
            RtsModeManager.onScroll(vertical);
            return;
        }
        if (!SidebarHudLayer.isVisible()) return;

        ci.cancel();

        int delta = vertical > 0 ? -1 : 1;
        SidebarHudLayer.cycleSelection(delta);
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) return;

        if (RtsModeManager.isActive()) {
            if (button == 0 || button == 1 || button == 2) {
                ci.cancel();
                RtsInputHandler.onMouseButton(button, action);
            }
            return;
        }

        if (button != 1 || action != 1) return;
        if (!SidebarHudLayer.isVisible()) return;
        if (SidebarHudLayer.getSelectedIndex() < 0) return;

        ci.cancel();

        SidebarHudLayer.executeSelectedButton();
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$cancelAccumulatedTurn(CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) return;
        if (RtsModeManager.isActive()) {
            ci.cancel();
        }
    }
}
