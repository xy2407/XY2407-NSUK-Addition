package com.xy2407.nsukaddition.mixin;

import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scoreboard 入队去重：成员已在目标队伍时跳过本次 addPlayerToTeam，避免 valarian_conquest 等外部模组
 * 对同一名成员反复加入同一队伍，进而触发 vanilla 内部的"先移除旧队再加入"广播，导致客户端收到 REMOVE
 * 数据包时本地队伍并无该成员而抛 IllegalStateException 坏档。
 */
@Mixin(Scoreboard.class)
public abstract class ScoreboardAddPlayerTeamDedupMixin {

    @Inject(method = "addPlayerToTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nsukaddition$skipIfAlreadyInTeam(String player, PlayerTeam team, CallbackInfoReturnable<Boolean> cir) {
        if (team != null && team.getPlayers().contains(player)) {
            cir.setReturnValue(true);
        }
    }
}