package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 视角下取消 KeyboardInput.tick，从根源阻止 WASD 读取按键绑定。 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$cancelKeyboardInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            ci.cancel();
        }
    }
}
