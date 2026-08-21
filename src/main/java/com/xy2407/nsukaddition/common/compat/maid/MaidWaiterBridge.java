package com.xy2407.nsukaddition.common.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 车万女仆软依赖桥：探测 maid 模组并提供女仆实体的雇佣/解雇/移动操作。
 * 所有方法签名仅使用原版类型（LivingEntity/UUID），方法体内部才接触 maid 类型，
 * 保证 maid 未安装时本类及调用方均可安全加载（与 LetFishLoveCompat 同模式）。
 */
@SuppressWarnings("null")
public final class MaidWaiterBridge {

    private MaidWaiterBridge() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("touhou_little_maid");
    }

    public static LivingEntity findMaid(ServerLevel level, UUID maidId) {
        if (!isLoaded() || level == null || maidId == null) return null;
        Entity entity = level.getEntity(maidId);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    public static List<LivingEntity> findTamedMaids(ServerLevel level, Player player) {
        if (!isLoaded() || level == null || player == null) return List.of();
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityMaid em && em.isTame() && player.getUUID().equals(em.getOwnerUUID())) {
                result.add(em);
            }
        }
        return List.copyOf(result);
    }

    public static boolean isOwnedBy(LivingEntity maid, UUID playerId) {
        return maid instanceof EntityMaid em && playerId != null && em.isTame() && playerId.equals(em.getOwnerUUID());
    }

    public static String displayName(LivingEntity maid) {
        return maid instanceof EntityMaid em && em.getDisplayName() != null
                ? em.getDisplayName().getString() : "Maid";
    }

    public static boolean assignRestaurantJob(ServerLevel level, LivingEntity maid, BlockPos boxPos) {
        if (!isLoaded() || !(maid instanceof EntityMaid em) || boxPos == null) return false;
        try {
            if (em.getTask() instanceof RestaurantMaidWaiterTask) return false;
            em.setSchedule(MaidSchedule.ALL);
            em.getSchedulePos().setWorkPos(boxPos);
            em.getSchedulePos().setIdlePos(boxPos);
            em.getSchedulePos().setSleepPos(boxPos);
            em.getSchedulePos().setDimension(level.dimension().location());
            em.getSchedulePos().setConfigured(true);
            em.setHomeModeEnable(true);
            em.setTask(new RestaurantMaidWaiterTask());
            return true;
        } catch (Exception e) {
            NsukAddition.LOGGER.error("指派女仆餐厅工作失败: {}", e.toString());
            return false;
        }
    }

    public static boolean releaseRestaurantJob(ServerLevel level, LivingEntity maid) {
        if (!isLoaded() || !(maid instanceof EntityMaid em)) return false;
        try {
            em.setSchedule(MaidSchedule.DAY);
            em.setHomeModeEnable(false);
            if (em.getTask() instanceof RestaurantMaidWaiterTask) {
                em.setTask(TaskManager.getIdleTask());
            }
            em.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return true;
        } catch (Exception e) {
            NsukAddition.LOGGER.error("女仆解雇恢复失败: {}", e.toString());
            return false;
        }
    }

    public static boolean isRestaurantWorker(LivingEntity maid) {
        return maid instanceof EntityMaid em && em.getTask() instanceof RestaurantMaidWaiterTask;
    }

    public static boolean isHomeRestricted(LivingEntity maid) {
        return maid instanceof EntityMaid em && em.isHomeModeEnable();
    }

    public static void updateRestaurantAnchor(LivingEntity maid, BlockPos stand) {
        if (!isLoaded() || !(maid instanceof EntityMaid em) || stand == null) return;
        try {
            SchedulePos schedulePos = em.getSchedulePos();
            if (!stand.equals(schedulePos.getWorkPos())) {
                schedulePos.setWorkPos(stand);
                schedulePos.setIdlePos(stand);
                schedulePos.setSleepPos(stand);
                schedulePos.setDimension(em.level().dimension().location());
                schedulePos.setConfigured(true);
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("女仆服务锚点更新失败: {}", e.toString());
        }
    }

    public static void moveMaid(ServerLevel level, LivingEntity maid, BlockPos target, float speed) {
        if (!isLoaded() || maid == null || target == null) return;
        if (maid instanceof Mob mob) {
            mob.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
        }
    }

    public static Vec3 position(LivingEntity maid) {
        return maid != null ? maid.position() : Vec3.ZERO;
    }

    public static double distToBlockSqr(LivingEntity maid, BlockPos target) {
        if (maid == null || target == null) return Double.MAX_VALUE;
        return maid.blockPosition().distSqr(target);
    }
}