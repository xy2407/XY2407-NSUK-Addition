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
 * 欢迎聊天改为仅首次进入触发：原版每次登录(handleLogin)都 sendWelcomeMessages，这里改为
 * 登录不再发；只在首次梦境音频被调度(首次进入、FIRST_DREAM 即将播放)时发送欢迎文字，
 * 使"文本与音频同源触发"。音频本身(FIRST_DREAM)仍由原版首次序列播放，不受影响。
 */
@Mixin(value = PlayerWelcomeService.class, remap = false)
public abstract class PlayerWelcomeServiceMixin {

    @Shadow(remap = false)
    private static void sendWelcomeMessages(ServerPlayer player) {
    }

    @Redirect(method = "handleLogin",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/event/PlayerWelcomeService;sendWelcomeMessages(Lnet/minecraft/server/level/ServerPlayer;)V"),
            remap = false)
    private static void nsuk$skipWelcomeOnEveryLogin(ServerPlayer player) {
    }

    @Inject(method = "scheduleFirstDreamSequence",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/concurrent/ConcurrentHashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false)
    private static void nsuk$welcomeOnFirstDreamSchedule(ServerPlayer player, CallbackInfo ci) {
        sendWelcomeMessages(player);
    }
}