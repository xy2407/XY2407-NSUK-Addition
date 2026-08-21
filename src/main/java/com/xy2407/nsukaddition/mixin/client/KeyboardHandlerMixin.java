package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.rts.RtsBuildingListHudLayer;
import com.xy2407.nsukaddition.client.rts.RtsBuildingPlacementManager;
import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 KeyboardHandler：RTS 底部建筑列表搜索框聚焦时捕获字符输入（退格/可见字符），避免流入聊天或游戏键位。 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$charTyped(long windowPointer, int codePoint, int modifiers, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        if (!RtsModeManager.isActive()) {
            return;
        }
        if (RtsBuildingListHudLayer.isSearchFocused()) {
            ci.cancel();
            RtsBuildingListHudLayer.handleChar(codePoint);
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$keyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        if (!RtsModeManager.isActive()) {
            return;
        }
        if (RtsBuildingPlacementManager.isMoveActive()
                && key == 256 && action == 1) {
            ci.cancel();
            RtsBuildingPlacementManager.endMove();
            return;
        }
        if (RtsBuildingListHudLayer.isSearchFocused() && key == 259 && action != 0) {
            ci.cancel();
            RtsBuildingListHudLayer.handleChar(259);
        }
    }
}
