package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 市民(静止)性能优化：对位置稳定的市民节流 travel(碰撞) 与 pushEntities，仅作用 simukraft 的 CitizenEntity。 */
@Mixin(LivingEntity.class)
public abstract class IdleCitizenThrottleMixin {

    /** 静止市民每跑一次 travel 的节流间隔(跳过其余,清理静止市民无意义的碰撞扫描)。 */
    private static final int TRAVEL_INTERVAL = 20;

    /** 上一次判定位置的姿态。 */
    @Unique
    private Vec3 nsukaddition$lastPos;

    /** 连续"位置稳定"计数。 */
    @Unique
    private int nsukaddition$stillTicks;

    /** travel: 位置稳定(=没真正在走)的市民只每 TRAVEL_INTERVAL tick 跑一次碰撞。 */
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$throttleIdleTravel(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof CitizenEntity) || self.level().isClientSide) {
            return;
        }
        if (!isStationarySafe(self)) {
            this.nsukaddition$lastPos = null;
            this.nsukaddition$stillTicks = 0;
            return;
        }
        Vec3 cur = self.position();
        boolean stable = this.nsukaddition$lastPos != null
                && cur.distanceToSqr(this.nsukaddition$lastPos) < 1.0E-7D;
        this.nsukaddition$lastPos = cur;
        if (!stable) {
            this.nsukaddition$stillTicks = 1;
            return;
        }
        this.nsukaddition$stillTicks++;
        if (this.nsukaddition$stillTicks % TRAVEL_INTERVAL != 0) {
            // 跳过本轮 travel→move→collide；清零速度避免恢复瞬间下坠/积重力。
            self.setDeltaMovement(0.0D, 0.0D, 0.0D);
            ci.cancel();
        }
    }

    /** pushEntities: 市民互相推挤节流为每 2 tick 一次。 */
    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true, require = 0)
    private void nsukaddition$throttlePushEntities(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof CitizenEntity) || self.level().isClientSide) {
            return;
        }
        if ((self.tickCount & 1) == 1) {
            ci.cancel();
        }
    }

    /**
     * isStationarySafe: 位置稳定优先于导航/速度判定——simukraft 的市民即使站着，
     * 其导航也可能保持"进行中"，故不依赖 isDone()，而用连续 tick 位置未变 + 贴地 + 无水平速度来认定静止。
     */
    private boolean isStationarySafe(LivingEntity self) {
        if (!self.onGround() || self.isInWater() || self.isInLava() || self.onClimbable()) {
            return false;
        }
        if (self.isPassenger() || self.isVehicle()) {
            return false;
        }
        if (self.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D) {
            return false;
        }
        // 竖直：允许贴地时的微小重力残差，但排除快速下坠(>0.12)或起跳(>0.05)。
        double vy = self.getDeltaMovement().y;
        return vy > -0.12D && vy < 0.05D;
    }
}