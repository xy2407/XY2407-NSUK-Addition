package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTS 模式拦截 KeyboardInput.tick：WASD 被相机占用，但原版键绑定仍把 WASD 写入玩家移动输入，
 * 客户端玩家实体按输入本地预测移动 → 走路动画 + 脚步声一直触发(即使服务端每 tick 拉回位置)。
 * 必须在 input.tick(玩家 tick 前重算 impulse)处拦截，Post tick 清零会被重算覆盖。
 */
@Mixin(KeyboardInput.class)
public abstract class ClientInputRtsMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$suppressInRts(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (RtsModeManager.isActive()) {
            Input input = (Input) (Object) this;
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.jumping = false;
            input.shiftKeyDown = false;
            ci.cancel();
        }
    }
}
