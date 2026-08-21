package com.xy2407.nsukaddition.common.citycore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 城市核心投影位置计算：玩家视线方向 4 格远处的方块坐标。 */
public final class CityCoreProjectionUtil {

    private static final double PROJECTION_DISTANCE = 7.0D;

    private CityCoreProjectionUtil() {
    }

    public static BlockPos projectionPos(Entity entity) {
        if (entity == null) {
            return BlockPos.ZERO;
        }
        Vec3 look = entity.getLookAngle();
        Vec3 eye = entity.getEyePosition(1.0F);
        Vec3 target = eye.add(look.x * PROJECTION_DISTANCE, look.y * PROJECTION_DISTANCE, look.z * PROJECTION_DISTANCE);
        return BlockPos.containing(target);
    }
}
