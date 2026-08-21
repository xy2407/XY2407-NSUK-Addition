package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.common.rts.path.SableStructureReader;
import com.xy2407.nsukaddition.server.combat.CitizenCombatService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * RTS 移动任务：驱动市民使用 SimuKraft 原版导航（CitizenNavigationService）移动到目标点。
 * 首次 tick 对目标做 ±1 格净空校验（零扫描），避免目标点在方块内部导致导航不可达；
 * 超远目标分段逼近，避免 SimuKraft 的远距离直接传送；导航卡住超时后标记完成，交由上层处理。
 * 实现 RtsTask 接口，供 RtsCitizenTaskManager 接管 NPC tick。
 */
public class RtsPhysicsMoveTask implements RtsTask {

    private static final double ARRIVE_DISTANCE = 0.8;

    private final Vec3 target;
    private Vec3 effectiveTarget = null;
    private boolean complete = false;
    private final boolean formationMode;
    private boolean chasing = false;
    private Deque<Vec3> mountWaypoints = null;
    private int mountRepathCooldown = 0;

    private boolean lastNavActive = false;
    private boolean navStarted = false;
    private int stuckTicks = 0;
    private Vec3 lastTickPos = null;

    private Vec3 stageTarget = null;
    private Vec3 lastRequestedTarget = null;

    public RtsPhysicsMoveTask(Vec3 target) {
        this(target, false);
    }

    public RtsPhysicsMoveTask(Vec3 target, boolean formationMode) {
        this.target = target;
        this.formationMode = formationMode;
    }

    @Override
    public void tick(CitizenEntity citizen) {
        if (complete) return;
        if (!(citizen.level() instanceof ServerLevel level)) return;

        RtsCitizenTaskManager.applyRtsSpeed(citizen, true);

        if (formationMode && CitizenCombatService.isMeleeFighter(citizen)
                && CitizenCombatService.isInCombat(citizen.getUUID())) {
            LivingEntity combatT = CitizenCombatService.getCombatTarget(citizen.getUUID());
            if (combatT != null && combatT.isAlive()) {
                chasing = true;
                effectiveTarget = combatT.position();
            } else {
                chasing = false;
                effectiveTarget = null;
            }
        } else if (chasing) {
            chasing = false;
            effectiveTarget = null;
        }

        if (citizen.isPassenger() && citizen.getVehicle() instanceof Mob mount) {
            tickMountNavigation(citizen, mount, level);
            return;
        }

        Vec3 current = citizen.position();
        if (lastTickPos == null) {
            lastTickPos = current;
        }
        if (effectiveTarget == null) {
            effectiveTarget = normalizeTarget(level, target);
        }

        if (!chasing && current.distanceToSqr(effectiveTarget) < ARRIVE_DISTANCE * ARRIVE_DISTANCE) {
            complete = true;
            RtsCitizenTaskManager.applyRtsSpeed(citizen, false);
            citizen.getNavigation().stop();
            citizen.setDeltaMovement(Vec3.ZERO);
            return;
        }

        runSimukraftNavigation(citizen, level);
    }

    private static Vec3 normalizeTarget(ServerLevel level, Vec3 target) {
        Vec3 world = SableStructureReader.projectOutOfSubLevel(level, target);
        BlockPos pos = BlockPos.containing(world);
        if (isStandable(level, pos)) {
            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
        BlockPos above = pos.above();
        if (isStandable(level, above)) {
            return new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        }
        return world;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return isAirOrEmpty(level, pos)
                && isAirOrEmpty(level, pos.above())
                && isSolidAny(level, pos.below());
    }

    private static boolean isAirOrEmpty(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return false;
        BlockState sub = SableStructureReader.getBlockStateAt(level, pos);
        return sub == null || sub.isAir();
    }

    private static boolean isSolidAny(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return true;
        BlockState sub = SableStructureReader.getBlockStateAt(level, pos);
        return sub != null && !sub.isAir();
    }

    private void tickMountNavigation(CitizenEntity citizen, Mob mount, ServerLevel level) {
        Vec3 mpos = mount.position();
        if (effectiveTarget == null) {
            effectiveTarget = normalizeTarget(level, target);
        }
        if (citizen.getVehicle() == mount) {
            citizen.setPos(citizen.getX(), mount.getY() + 0.9D, citizen.getZ());
        }
        if (mpos.distanceToSqr(effectiveTarget) < ARRIVE_DISTANCE * ARRIVE_DISTANCE) {
            complete = true;
            RtsCitizenTaskManager.applyRtsSpeed(citizen, false);
            mount.getNavigation().stop();
            mount.setDeltaMovement(0.0D, mount.getDeltaMovement().y, 0.0D);
            mount.zza = 0.0F;
            citizen.zza = 0.0F;
            return;
        }
        if ((mountWaypoints == null || mountWaypoints.isEmpty()) && --mountRepathCooldown <= 0) {
            List<BlockPos> path = MountPathfinder.findPath(level, mount,
                    mount.blockPosition(), BlockPos.containing(effectiveTarget));
            if (path != null) {
                ArrayDeque<Vec3> q = new ArrayDeque<>();
                for (BlockPos p : path) {
                    q.add(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
                }
                mountWaypoints = q;
            }
            mountRepathCooldown = 20;
        }
        if (mountWaypoints != null) {
            while (!mountWaypoints.isEmpty()) {
                Vec3 head = mountWaypoints.peek();
                double dx = head.x - mpos.x;
                double dz = head.z - mpos.z;
                if (dx * dx + dz * dz < 0.25D) {
                    mountWaypoints.poll();
                } else {
                    break;
                }
            }
            if (!mountWaypoints.isEmpty()) {
                Vec3 next = mountWaypoints.peek();
                Vec3 toTarget = next.subtract(mpos);
                double hDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
                if (hDist > 0.5D) {
                    float yaw = (float) (Math.atan2(toTarget.z, toTarget.x) * 180.0D / Math.PI) - 90.0F;
                    mount.setYRot(yaw);
                    mount.yBodyRot = yaw;
                    mount.yHeadRot = yaw;
                    double speed = mount.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 1.1D;
                    Vec3 vel = new Vec3(toTarget.x, 0.0D, toTarget.z).normalize().scale(speed);
                    mount.setDeltaMovement(vel.x, mount.getDeltaMovement().y, vel.z);
                    mount.zza = 1.0F;
                    citizen.zza = 1.0F;
                } else {
                    mount.setDeltaMovement(0.0D, mount.getDeltaMovement().y, 0.0D);
                    mount.zza = 0.0F;
                    citizen.zza = 0.0F;
                }
                return;
            }
        }
        mount.setDeltaMovement(0.0D, mount.getDeltaMovement().y, 0.0D);
        mount.zza = 0.0F;
        citizen.zza = 0.0F;
    }

    private void runSimukraftNavigation(CitizenEntity citizen, ServerLevel level) {
        Vec3 current = citizen.position();

        double far = common.cn.kafei.simukraft.config.ServerConfig.pathFarMovementTeleportDistance();
        double safety = Math.max(16.0, far - 12.0);

        Vec3 requestTarget = effectiveTarget;
        if (current.distanceTo(effectiveTarget) >= far) {
            if (stageTarget == null || current.distanceTo(stageTarget) < 8.0) {
                Vec3 dir = effectiveTarget.subtract(current).normalize();
                stageTarget = current.add(dir.scale(safety));
            }
            requestTarget = stageTarget;
        } else {
            stageTarget = null;
        }

        PathNavigation nav = citizen.getNavigation();
        boolean done = nav.isDone();
        boolean arrived = current.distanceToSqr(effectiveTarget) < ARRIVE_DISTANCE * ARRIVE_DISTANCE;
        if (!lastNavActive || !requestTarget.equals(lastRequestedTarget)
                || (done && !arrived && lastNavActive )) {
            boolean accepted = common.cn.kafei.simukraft.path.CitizenNavigationService.requestMove(
                    level, citizen.getUUID(), requestTarget, common.cn.kafei.simukraft.path.MovementIntent.WALK);
            if (accepted || common.cn.kafei.simukraft.path.CitizenNavigationService.isNavigating(level, citizen.getUUID())) {
                lastNavActive = true;
                navStarted = true;
                lastRequestedTarget = requestTarget;
            } else {
                lastNavActive = false;
            }
        }

        if (stageTarget != null && current.distanceTo(stageTarget) < 6.0) {
            stageTarget = null;
            lastNavActive = false;
        }

        Vec3 posNow = citizen.position();
        double moved = posNow.distanceTo(lastTickPos);
        lastTickPos = posNow;
        boolean inCombat = CitizenCombatService.isInCombat(citizen.getUUID());
        if (navStarted && done && moved < 0.02D && !arrived && !inCombat) {
            stuckTicks++;
            if (stuckTicks > 100) {
                complete = true;
                RtsCitizenTaskManager.applyRtsSpeed(citizen, false);
                nav.stop();
                citizen.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            stuckTicks = Math.max(0, stuckTicks - 1);
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void onCancel() {
        complete = true;
        stageTarget = null;
        lastRequestedTarget = null;
        lastNavActive = false;
        navStarted = false;
        stuckTicks = 0;
        lastTickPos = null;
    }
}