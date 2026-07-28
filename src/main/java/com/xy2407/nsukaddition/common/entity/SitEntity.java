package com.xy2407.nsukaddition.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 不可见坐骑实体，NPC 骑乘后触发坐船动画，无物理无碰撞。 */
public class SitEntity extends Entity {

    public static final EntityType<SitEntity> TYPE = EntityType.Builder.<SitEntity>of(SitEntity::new, MobCategory.MISC)
            .sized(0.0F, 0.0F).clientTrackingRange(10).noSummon().build("nsuk_sit");

    public SitEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public SitEntity(Level level, BlockPos pos) {
        this(TYPE, level);
        setPos(pos.getX() + 0.5, pos.getY() + 0.3125, pos.getZ() + 0.5);
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return super.getPassengerRidingPosition(passenger).add(0, -0.3125, 0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (getPassengers().isEmpty()) { discard(); return; }
        }
    }

    @Override public boolean isInvisible() { return true; }
    @Override public boolean isInvisibleTo(Player p) { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return false; }
    @Override public boolean shouldRender(double x, double y, double z) { return false; }
    @Override public boolean displayFireAnimation() { return false; }
}
