package com.xy2407.nsukaddition.mixin.client;

import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端 Scoreboard 移队兜底：收到 ClientboundSetPlayerTeamPacket 的 REMOVE 时，若本地目标队伍
 * 并不包含该成员（valarian_conquest 等外部模组重复/失序的加队导致的队伍状态不同步），vanilla 的
 * Scoreboard.removePlayerFromTeam 会抛 IllegalStateException 坏档。这里在本地成员缺失时静默跳过
 * 整个移除，不抛异常。
 */
@Mixin(Scoreboard.class)
public abstract class ScoreboardRemovePlayerTeamSafetyMixin {

    @Inject(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V",
            at = @At("HEAD"), cancellable = true)
    private void nsukaddition$guardRemove(String name, PlayerTeam team, CallbackInfo ci) {
        if (team == null || !team.getPlayers().contains(name)) {
            ci.cancel();
        }
    }
}