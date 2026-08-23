package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.event.PlayerWelcomeService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 首次梦境策略：
 * - 音频：完全禁用。拦截 scheduleFirstDreamSequence 使其不再填充首次梦境队列，tick 里便不会播放音乐。
 * - 欢迎文字：仅玩家首次进入世界时发送一次，用本模组持久标记 nsukaddition_first_dream_played 判定，
 *   与 SimKraft 延迟写入的 simukraft_first_dream_played 无关，重启世界也保持只弹一次。
 */
@Mixin(value = PlayerWelcomeService.class, remap = false)
public abstract class PlayerWelcomeServiceMixin {

    private static final String OWN_FLAG = "nsukaddition_first_dream_played";

    @Shadow(remap = false)
    private static void sendWelcomeMessages(ServerPlayer player) {
    }

    @Redirect(method = "handleLogin",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/event/PlayerWelcomeService;scheduleFirstDreamSequence(Lnet/minecraft/server/level/ServerPlayer;)V"),
            remap = false)
    private static void nsuk$disableFirstDreamAudio(ServerPlayer player) {
    }

    @Redirect(method = "handleLogin",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/event/PlayerWelcomeService;sendWelcomeMessages(Lnet/minecraft/server/level/ServerPlayer;)V"),
            remap = false)
    private static void nsuk$blockWelcomeOnEveryLogin(ServerPlayer player) {
    }

    @Inject(method = "handleLogin", at = @At("HEAD"), remap = false)
    private static void nsuk$welcomeOnFirstLogin(ServerPlayer player, CallbackInfo ci) {
        if (!hasOwnFlag(player)) {
            sendWelcomeMessages(player);
            setOwnFlag(player);
        }
    }

    private static boolean hasOwnFlag(ServerPlayer player) {
        return player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG)
                .getBoolean(OWN_FLAG);
    }

    private static void setOwnFlag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag data = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        data.putBoolean(OWN_FLAG, true);
        persistent.put(Player.PERSISTED_NBT_TAG, data);
    }
}