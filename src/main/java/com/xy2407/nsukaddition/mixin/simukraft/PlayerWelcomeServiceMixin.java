package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.event.PlayerWelcomeService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 欢迎聊天改为仅首次进入触发：原版每次登录(handleLogin)都会 sendWelcomeMessages，
 * 这里把每次登录的欢迎屏蔽，改为只在玩家首次进入(hasPlayedFirstDream == false)时发送一次，
 * 与首次梦境音频(FIRST_DREAM)同源触发；音频本身仍由原版首次序列播放，不受影响。
 */
@Mixin(value = PlayerWelcomeService.class, remap = false)
public abstract class PlayerWelcomeServiceMixin {

    @Shadow(remap = false)
    private static void sendWelcomeMessages(ServerPlayer player) {
    }

    @Shadow(remap = false)
    private static boolean hasPlayedFirstDream(ServerPlayer player) {
        throw new AssertionError();
    }

    @Inject(method = "handleLogin", at = @At("HEAD"), remap = false)
    private static void nsuk$welcomeOnFirstLogin(ServerPlayer player, CallbackInfo ci) {
        if (!hasPlayedFirstDream(player)) {
            sendWelcomeMessages(player);
        }
    }

    @Redirect(method = "handleLogin",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/event/PlayerWelcomeService;sendWelcomeMessages(Lnet/minecraft/server/level/ServerPlayer;)V"),
            remap = false)
    private static void nsuk$skipWelcomeOnEveryLogin(ServerPlayer player) {
    }
}