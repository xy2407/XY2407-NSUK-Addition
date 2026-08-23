package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.common.rts.RtsPlayerState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 玩家实体处理服务：记录 RTS 驱动的玩家UUID，每 tick 清空输入+清零运动向量并保持无敌/无重力，
 * 每 10 tick 用 teleportTo 把玩家实体直接同步到假人所在位置并对齐朝向（区块加载中心跟随假人）。
 */
public final class RtsFakePlayerSyncService {

    private static final int SYNC_INTERVAL = 10;

    private RtsFakePlayerSyncService() {
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        boolean shouldSync = gameTime % SYNC_INTERVAL == 0;
        Set<UUID> activeThisTick = new HashSet<>();

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof RtsFakePlayerEntity fake)) {
                continue;
            }
            UUID ownerId = fake.getOwnerUUID();
            if (ownerId == null) {
                continue;
            }
            activeThisTick.add(ownerId);
            RtsPlayerState.mark(ownerId);

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
            if (player == null || !player.isAlive()) {
                continue;
            }

            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (!player.isNoGravity()) {
                player.setNoGravity(true);
            }
            if (!player.isInvulnerable()) {
                player.setInvulnerable(true);
            }

            if (shouldSync) {
                Vec3 fakePos = fake.position();
                player.teleportTo(fakePos.x, fakePos.y, fakePos.z);
                player.setYRot(fake.getYRot());
                player.setXRot(fake.getXRot());
                player.yBodyRot = fake.yBodyRot;
                player.yHeadRot = fake.yHeadRot;
            }
        }

        for (UUID id : RtsPlayerState.all()) {
            if (activeThisTick.contains(id)) {
                continue;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                if (p.isNoGravity()) {
                    p.setNoGravity(false);
                }
                if (p.isInvulnerable()) {
                    p.setInvulnerable(false);
                }
            }
            RtsPlayerState.clear(id);
        }
    }

    public static void clearPlayer(UUID playerId) {
        RtsPlayerState.clear(playerId);
    }

    public static void clearAll() {
        RtsPlayerState.all().clear();
    }
}