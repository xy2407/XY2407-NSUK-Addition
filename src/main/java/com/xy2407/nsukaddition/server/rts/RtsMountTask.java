package com.xy2407.nsukaddition.server.rts;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** RTS 上马任务：NPC 走到马旁(2 格)后 startRiding 骑乘。马消失/已被骑则放弃。 */
public class RtsMountTask implements RtsTask {

    private static final double MOUNT_APPROACH_DIST = 2.5D;

    private final UUID mountId;
    private boolean complete = false;

    public RtsMountTask(UUID mountId) {
        this.mountId = mountId;
    }

    @Override
    public void tick(CitizenEntity citizen) {
        if (complete) return;
        if (!(citizen.level() instanceof ServerLevel level)) return;
        Entity mount = level.getEntity(mountId);
        if (!(mount instanceof AbstractHorse horse) || horse.isRemoved() || horse.isVehicle()) {
            complete = true;
            return;
        }
        if (citizen.getVehicle() == horse) {
            complete = true;
            return;
        }
        Vec3 target = horse.position();
        if (citizen.position().distanceToSqr(target) < MOUNT_APPROACH_DIST * MOUNT_APPROACH_DIST) {
            citizen.startRiding(horse, true);
            complete = true;
            return;
        }
        if (!CitizenNavigationService.isNavigating(level, citizen.getUUID())) {
            CitizenNavigationService.requestMove(level, citizen.getUUID(), target, MovementIntent.WALK);
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void onCancel() {
        complete = true;
    }
}
