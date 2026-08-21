package com.xy2407.nsukaddition.server.rts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 服务端 RTS 玩家移动驱动：每 tick 让玩家朝目标点前进，遇障碍自动跳跃。 */
public final class RtsPlayerMoveService {

    private static final double MOVE_SPEED = 0.28D;
    private static final double ARRIVE_DISTANCE = 0.8D;
    private static final double JUMP_CHECK_DISTANCE = 0.55D;
    private static final double MAX_STEP_HEIGHT = 1.0D;
    private static final long TIMEOUT_TICKS = 600L;

    private static final Map<UUID, MoveTask> TASKS = new ConcurrentHashMap<>();

    private RtsPlayerMoveService() {
    }

    public static void requestMove(Player player, Vec3 target) {
        if (player == null || target == null) return;
        TASKS.put(player.getUUID(), new MoveTask(target, player.level().getGameTime()));
    }

    public static void cancelMove(UUID playerId) {
        if (playerId != null) TASKS.remove(playerId);
    }

    public static void clearAll() {
        TASKS.clear();
    }

    public static void tick(ServerLevel level) {
        if (TASKS.isEmpty()) return;
        long currentTick = level.getGameTime();
        Iterator<Map.Entry<UUID, MoveTask>> iterator = TASKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MoveTask> entry = iterator.next();
            UUID id = entry.getKey();
            MoveTask task = entry.getValue();

            if (currentTick - task.startTick > TIMEOUT_TICKS) {
                iterator.remove();
                continue;
            }

            net.minecraft.world.entity.Entity entity = level.getServer().getPlayerList().getPlayer(id);
            if (!(entity instanceof ServerPlayer player)) {
                iterator.remove();
                continue;
            }
            if (player.serverLevel() != level) continue;

            drivePlayer(player, task.target, currentTick, iterator);
        }
    }

    private static void drivePlayer(ServerPlayer player, Vec3 target, long currentTick, Iterator<Map.Entry<UUID, MoveTask>> iterator) {
        Vec3 current = player.position();
        double dx = target.x - current.x;
        double dz = target.z - current.z;
        double horizontalDistSqr = dx * dx + dz * dz;

        if (horizontalDistSqr < ARRIVE_DISTANCE * ARRIVE_DISTANCE
                && Math.abs(target.y - current.y) < 2.0D) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, motion.y, 0.0D);
            iterator.remove();
            return;
        }

        double horizontalDist = Math.sqrt(horizontalDistSqr);
        if (horizontalDist < 1.0E-4D) {
            iterator.remove();
            return;
        }
        double dirX = dx / horizontalDist;
        double dirZ = dz / horizontalDist;

        float yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        player.setYRot(yaw);
        player.yHeadRot = yaw;

        if (shouldJump(player, dirX, dirZ)) {
            if (player.onGround()) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, 0.42D, motion.z);
            }
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(dirX * MOVE_SPEED, motion.y, dirZ * MOVE_SPEED);
    }

    private static boolean shouldJump(ServerPlayer player, double dirX, double dirZ) {
        if (!player.onGround()) return false;
        BlockPos frontPos = BlockPos.containing(player.getX() + dirX * JUMP_CHECK_DISTANCE, player.getY(), player.getZ() + dirZ * JUMP_CHECK_DISTANCE);
        ServerLevel level = player.serverLevel();
        BlockState frontBlock = level.getBlockState(frontPos);
        BlockState aboveFront = level.getBlockState(frontPos.above());
        if (!frontBlock.isAir() && !aboveFront.isAir()) {
            double blockTopY = frontPos.getY() + 1.0D;
            if (blockTopY - player.getY() > MAX_STEP_HEIGHT) {
                return true;
            }
        }
        if (!frontBlock.isAir() && aboveFront.isAir()) {
            double blockTopY = frontPos.getY() + 1.0D;
            return blockTopY - player.getY() > 0.5D;
        }
        return false;
    }

    private record MoveTask(Vec3 target, long startTick) {
    }
}
