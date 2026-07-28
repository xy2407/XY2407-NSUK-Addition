package com.xy2407.nsukaddition.common.breeding;

import com.chinaex123.letfishlove.capabilities.FishBreedingCap;
import com.chinaex123.letfishlove.capabilities.FishBreedingCapAttacher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/** 岩浆鱼繁殖 AI 目标，基于 LFLR 模组能力实现配对交配。 */
public class LavaFishBreedGoal extends Goal {

    private static final TargetingConditions PARTNER_TARGETING = TargetingConditions.forNonCombat().range(8.0D).ignoreLineOfSight();

    protected final PathfinderMob fish;
    private final Class<? extends PathfinderMob> partnerClass;
    protected final Level level;
    @Nullable
    protected PathfinderMob partner;
    private int loveTime;
    private final double speedModifier;

    public LavaFishBreedGoal(PathfinderMob fish, double speedModifier) {
        this(fish, speedModifier, fish.getClass());
    }

    @SuppressWarnings("unchecked")
    public LavaFishBreedGoal(PathfinderMob fish, double speedModifier, Class<? extends PathfinderMob> partnerClass) {
        this.fish = fish;
        this.level = fish.level();
        this.partnerClass = partnerClass;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
        if (cap == null || !cap.isInLove() || cap.isPregnant()) {
            return false;
        }
        this.partner = this.getFreePartner();
        return this.partner != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.partner != null && this.partner.isAlive()
                && isPartnerInLove() && this.loveTime < 100;
    }

    private boolean isPartnerInLove() {
        FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(partner.getUUID());
        return cap != null && cap.isInLove();
    }

    @Override
    public void stop() {
        this.partner = null;
        this.loveTime = 0;
    }

    @Override
    public void tick() {
        if (partner == null) return;
        this.fish.getLookControl().setLookAt(this.partner, 10.0F, this.fish.getMaxHeadXRot());
        this.fish.getNavigation().moveTo(this.partner, this.speedModifier);
        ++this.loveTime;
        if (this.loveTime >= this.adjustedTickDelay(100) && this.fish.distanceToSqr(this.partner) < 9.0D) {
            this.breed();
        }
    }

    @Nullable
    private PathfinderMob getFreePartner() {
        List<? extends PathfinderMob> list = this.level.getNearbyEntities(this.partnerClass, PARTNER_TARGETING, this.fish, this.fish.getBoundingBox().inflate(8.0D));
        double d0 = Double.MAX_VALUE;
        PathfinderMob partner = null;
        for (PathfinderMob other : list) {
            if (canMate(this.fish, other) && this.fish.distanceToSqr(other) < d0) {
                partner = other;
                d0 = this.fish.distanceToSqr(other);
            }
        }
        return partner;
    }

    private static boolean canMate(PathfinderMob a, PathfinderMob b) {
        if (b == a || b.getClass() != a.getClass()) return false;
        FishBreedingCap capA = FishBreedingCapAttacher.CAPABILITY_CACHE.get(a.getUUID());
        FishBreedingCap capB = FishBreedingCapAttacher.CAPABILITY_CACHE.get(b.getUUID());
        return capA != null && capB != null && capA.isInLove() && capB.isInLove();
    }

    protected void breed() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        FishBreedingCap thisCap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
        FishBreedingCap otherCap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(partner.getUUID());
        if (thisCap == null || otherCap == null) return;

        ServerPlayer cause = thisCap.getLoveCause(serverLevel);
        if (cause == null && otherCap.getLoveCause(serverLevel) != null) {
            cause = otherCap.getLoveCause(serverLevel);
        }
        if (cause != null) {
            cause.awardStat(Stats.ANIMALS_BRED);
        }
        thisCap.resetLove();
        otherCap.resetLove();
        thisCap.setCanLoveCooldown(6000, true);
        otherCap.setCanLoveCooldown(6000, true);
        thisCap.setPregnant(true, true);
        serverLevel.broadcastEntityEvent(fish, (byte) 18);
        if (serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)) {
            serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, fish.getX(), fish.getY(), fish.getZ(), fish.getRandom().nextInt(7) + 1));
        }
        fish.gameEvent(GameEvent.ENTITY_ACTION);
    }
}
