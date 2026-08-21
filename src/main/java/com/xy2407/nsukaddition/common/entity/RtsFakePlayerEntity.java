package com.xy2407.nsukaddition.common.entity;

import com.xy2407.nsukaddition.common.rts.path.FakePlayerPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RTS 假人实体：玩家替身，服务端生成并同步，客户端用玩家模型渲染，可被选中并接受移动命令。
 * 移动基于自研四面通道寻路(FakePlayerPathfinder)驱动。
 */
public class RtsFakePlayerEntity extends LivingEntity {

    public static final EntityType<RtsFakePlayerEntity> TYPE = EntityType.Builder.<RtsFakePlayerEntity>of(RtsFakePlayerEntity::new, MobCategory.MISC)
            .sized(0.6F, 1.8F).clientTrackingRange(20).updateInterval(1).noSummon().build("rts_fake_player");

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(RtsFakePlayerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final double MOVE_SPEED = 0.28D;
    private static final double SWIM_SPEED = 0.20D;
    private static final double ARRIVE_DISTANCE = 0.8D;
    private static final int REPATH_INTERVAL = 60;
    private static final double WAYPOINT_ARRIVE_DISTANCE = 0.4D;
    private static final int MAX_WAYPOINT_ATTEMPT_TICKS = 80;
    private static final double DOOR_OPEN_RANGE = 2.5D;
    private static final double INTERACT_RANGE = 5.0D;

    private Vec3 targetPos = null;
    private List<BlockPos> path = null;
    private int waypointIndex = 0;
    private int waypointStuckTicks = 0;
    private int doorSlideTick = 0;
    private boolean doorSlideLeft = true;
    private int jumpCooldown = 0;
    private boolean swimming = false;
    private int repathCooldown = 0;
    private BlockPos openedDoorPos = null;
    private int doorCloseDelay = 0;
    private BlockPos interactTarget = null;
    private Direction interactDirection = null;
    private Vec3 interactLocation = null;

    public RtsFakePlayerEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    public RtsFakePlayerEntity(Level level, Vec3 pos, UUID ownerUUID) {
        this(TYPE, level);
        setPos(pos.x, pos.y, pos.z);
        getEntityData().set(OWNER_UUID, Optional.of(ownerUUID));
    }

    public UUID getOwnerUUID() {
        return getEntityData().get(OWNER_UUID).orElse(new UUID(0L, 0L));
    }

    public void setTarget(Vec3 target) {
        this.targetPos = target;
        this.path = null;
        this.waypointIndex = 0;
        this.waypointStuckTicks = 0;
        this.doorSlideTick = 0;
        this.swimming = false;
        this.repathCooldown = 0;
        this.interactTarget = null;
        this.interactDirection = null;
        if (openedDoorPos != null) {
            setDoorOpen(openedDoorPos, false);
            openedDoorPos = null;
        }
        doorCloseDelay = 0;
    }

    public void setInteractTarget(BlockPos pos, Direction dir, Vec3 location) {
        this.setTarget(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        this.interactTarget = pos;
        this.interactDirection = dir;
        this.interactLocation = location;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (targetPos == null) {
            stopHorizontal();
            swimming = false;
            return;
        }

        if (path == null) {
            if (repathCooldown > 0) {
                repathCooldown--;
                stopHorizontal();
                return;
            }
            if (position().distanceToSqr(targetPos) < 4.0) {
                moveDirect();
                return;
            }
            if (level() instanceof ServerLevel serverLevel) {
                BlockPos start = blockPosition();
                Vec3 worldTarget = com.xy2407.nsukaddition.common.rts.path.SableStructureReader.projectOutOfSubLevel(serverLevel, targetPos);
                path = FakePlayerPathfinder.findPath(serverLevel, start, BlockPos.containing(worldTarget));
                waypointIndex = 0;
                waypointStuckTicks = 0;
                doorSlideTick = 0;
                repathCooldown = REPATH_INTERVAL;
                if (path == null || path.isEmpty()) {
                    moveDirect();
                    return;
                }
            } else {
                moveDirect();
            }
        }

        if (openedDoorPos != null) {
            doorCloseDelay++;
            if (doorCloseDelay > 40) {
                setDoorOpen(openedDoorPos, false);
                openedDoorPos = null;
                doorCloseDelay = 0;
            }
        }

        Vec3 current = position();
        if (interactTarget != null && current.distanceToSqr(Vec3.atCenterOf(interactTarget)) < INTERACT_RANGE * INTERACT_RANGE) {
            stopHorizontal();
            targetPos = null;
            path = null;
            swimming = false;
            tryTriggerInteract();
            return;
        }

        if (path == null && current.distanceToSqr(targetPos) < ARRIVE_DISTANCE * ARRIVE_DISTANCE) {
            stopHorizontal();
            targetPos = null;
            swimming = false;
            tryTriggerInteract();
            return;
        }

        if (path != null) {
            followPath();
            return;
        }

        moveDirect();
    }

    private void moveToward(Vec3 target, boolean wpSwim) {
        Vec3 current = position();
        double dx = target.x - current.x;
        double dz = target.z - current.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        Vec3 motion = getDeltaMovement();
        if (wpSwim || isInWater()) {
            swimming = true;
            double yTarget = waterSurfaceY() - 0.4D;
            double dy = yTarget - current.y;
            double newY = motion.y + (dy > 0.3D ? 0.08D : (dy < -0.3D ? -0.08D : 0.0D));
            if (newY > 0.15D) newY = 0.15D;
            if (newY < -0.15D) newY = -0.15D;
            if (hDist > 1.0E-4D) {
                setDeltaMovement(dx / hDist * SWIM_SPEED, newY, dz / hDist * SWIM_SPEED);
            } else {
                setDeltaMovement(0.0D, newY, 0.0D);
            }
        } else {
            swimming = false;
            if (hDist > 1.0E-4D) {
                setDeltaMovement(dx / hDist * MOVE_SPEED, motion.y, dz / hDist * MOVE_SPEED);
            } else {
                setDeltaMovement(0.0D, motion.y, 0.0D);
            }
        }
        if (hDist >= 0.6D) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    private void followPath() {
        if (path == null || path.isEmpty()) {
            return;
        }
        if (waypointIndex >= path.size()) {
            path = null;
            moveDirect();
            return;
        }
        BlockPos wp = path.get(waypointIndex);
        Vec3 wpCenter = new Vec3(wp.getX() + 0.5D, wp.getY(), wp.getZ() + 0.5D);
        double wpx = wpCenter.x - position().x;
        double wpz = wpCenter.z - position().z;
        double wpDistSq = wpx * wpx + wpz * wpz;
        if (wpDistSq < WAYPOINT_ARRIVE_DISTANCE * WAYPOINT_ARRIVE_DISTANCE) {
            waypointIndex++;
            waypointStuckTicks = 0;
            doorSlideTick = 0;
            return;
        }
        waypointStuckTicks++;
        if (waypointStuckTicks > MAX_WAYPOINT_ATTEMPT_TICKS) {
            waypointIndex++;
            waypointStuckTicks = 0;
            doorSlideTick = 0;
            return;
        }
        if (waypointStuckTicks > 20 && isDoorNear(wpCenter) && doorSlideTick <= 0) {
            doorSlideTick = 10;
            doorSlideLeft = !doorSlideLeft;
            double yawRad = Math.toRadians(getYRot());
            double off = doorSlideLeft ? 1.5D : -1.5D;
            Vec3 slide = new Vec3(wpCenter.x + Math.cos(yawRad) * off, wpCenter.y, wpCenter.z + Math.sin(yawRad) * off);
            moveToward(slide, false);
            return;
        }
        if (doorSlideTick > 0) {
            doorSlideTick--;
        }
        if (wp.getY() > position().y + 0.45D && jumpCooldown <= 0) {
            jumpFromGround();
            jumpCooldown = 10;
        }
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
        tryOpenDoorIfPresent(wp);
        boolean wpSwim = level().getBlockState(wp).getFluidState().is(net.minecraft.world.level.material.Fluids.WATER);
        moveToward(wpCenter, wpSwim);
    }

    private boolean isDoorNear(Vec3 center) {
        BlockPos base = BlockPos.containing(center);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState s = level().getBlockState(base.offset(dx, 0, dz));
                if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapDoorBlock || s.getBlock() instanceof FenceGateBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private void moveDirect() {
        Vec3 current = position();
        double dx = targetPos.x - current.x;
        double dz = targetPos.z - current.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist >= 0.6D) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        if (dist < ARRIVE_DISTANCE) {
            stopHorizontal();
            targetPos = null;
            path = null;
            swimming = false;
            tryTriggerInteract();
            return;
        }
        Vec3 motion = getDeltaMovement();
        if (isInWater()) {
            swimming = true;
            double yTarget = waterSurfaceY() - 0.4D;
            double dy = yTarget - current.y;
            double newY = motion.y + (dy > 0.3D ? 0.08D : (dy < -0.3D ? -0.08D : 0.0D));
            if (newY > 0.15D) newY = 0.15D;
            if (newY < -0.15D) newY = -0.15D;
            setDeltaMovement((dx / dist) * SWIM_SPEED, newY, (dz / dist) * SWIM_SPEED);
        } else {
            swimming = false;
            setDeltaMovement((dx / dist) * MOVE_SPEED, motion.y, (dz / dist) * MOVE_SPEED);
        }
    }

    private double waterSurfaceY() {
        Vec3 pos = position();
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int y = (int) Math.floor(pos.y);
        for (int i = y; i < y + 8; i++) {
            if (!level().getBlockState(new BlockPos(x, i, z)).getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) {
                return i;
            }
        }
        return pos.y;
    }

    private void tryOpenDoorIfPresent(BlockPos pos) {
        if (pos == null || openedDoorPos != null) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        BlockState state = serverLevel.getBlockState(pos);
        boolean isDoor = state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
        if (!isDoor || isDoorOpen(state)) {
            return;
        }
        if (position().distanceToSqr(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                < DOOR_OPEN_RANGE * DOOR_OPEN_RANGE) {
            if (setDoorOpen(pos, true)) {
                openedDoorPos = pos;
                doorCloseDelay = 0;
            }
        }
    }

    private static boolean isDoorOpen(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    private boolean setDoorOpen(BlockPos pos, boolean open) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        BlockState state = serverLevel.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN)) return false;

        BlockState newState = state.setValue(BlockStateProperties.OPEN, open);
        if (state.getBlock() instanceof DoorBlock) {
            BlockState above = serverLevel.getBlockState(pos.above());
            if (above.getBlock() instanceof DoorBlock) {
                serverLevel.setBlockAndUpdate(pos.above(), above.setValue(BlockStateProperties.OPEN, open));
            }
            BlockState below = serverLevel.getBlockState(pos.below());
            if (below.getBlock() instanceof DoorBlock) {
                serverLevel.setBlockAndUpdate(pos.below(), below.setValue(BlockStateProperties.OPEN, open));
            }
        }
        serverLevel.setBlockAndUpdate(pos, newState);
        return true;
    }

    private void stopHorizontal() {
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0.0D, motion.y, 0.0D);
    }

    private void tryTriggerInteract() {
        if (interactTarget == null || interactDirection == null) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        UUID ownerUUID = getOwnerUUID();
        net.minecraft.server.level.ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null) {
            interactTarget = null;
            interactDirection = null;
            return;
        }
        BlockState state = serverLevel.getBlockState(interactTarget);
        if (!state.isAir()) {
            Vec3 useLocation = interactLocation != null ? interactLocation : Vec3.atCenterOf(interactTarget);
            BlockHitResult hit = new BlockHitResult(useLocation, interactDirection, interactTarget, false);
            state.useWithoutItem(serverLevel, owner, hit);
        }
        interactTarget = null;
        interactDirection = null;
    }

    @Override
    public boolean isPickable() { return true; }
    @Override
    public boolean isPushable() { return false; }
    @Override
    public boolean canCollideWith(net.minecraft.world.entity.Entity other) { return false; }
    @Override
    public void push(net.minecraft.world.entity.Entity other) { }
    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource src) { return true; }
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource src, float amount) { return false; }
    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override
    public boolean shouldRender(double x, double y, double z) { return true; }

    @Override
    public Iterable<ItemStack> getArmorSlots() { return Collections.emptyList(); }
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    @Override
    public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            getEntityData().set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUUID();
        if (owner.getMostSignificantBits() != 0L || owner.getLeastSignificantBits() != 0L) {
            tag.putUUID("OwnerUUID", owner);
        }
    }
}