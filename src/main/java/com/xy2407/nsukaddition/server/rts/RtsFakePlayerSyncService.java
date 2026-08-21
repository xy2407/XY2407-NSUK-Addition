package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 玩家实体处理服务：
 * RTS 期间玩家实体不进行任何行为（每 tick 清空输入+清零运动向量），
 * 每 10 tick 用 teleportTo 把玩家实体同步到假人周围任意一面的 1 格处（与假人不重叠，区块加载中心跟随假人）。
 * 玩家实体保持渲染（不 setInvisible），便于观察假人移动时玩家实体的实际位置/行为。
 * 必须 teleportTo 而非 setPos：setPos 不触发服务端区块加载中心更新，假人走到加载边缘会卡。
 */
public final class RtsFakePlayerSyncService {

    private static final int SYNC_INTERVAL = 10;

    private static final Set<UUID> ACTIVE = new HashSet<>();

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
            ACTIVE.add(ownerId);

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
            if (player.isInvisible()) {
                player.setInvisible(false);
            }

            if (shouldSync) {
                Vec3 fakePos = fake.position();
                float yawRad = (float) Math.toRadians(fake.getYRot());
                double leftX = Math.cos(yawRad);
                double leftZ = Math.sin(yawRad);
                player.teleportTo(fakePos.x + leftX, fakePos.y, fakePos.z + leftZ);
            }
        }

        for (UUID id : ACTIVE) {
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
            ACTIVE.remove(id);
        }
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            ACTIVE.remove(playerId);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
