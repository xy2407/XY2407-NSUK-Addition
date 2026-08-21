package com.xy2407.nsukaddition.server.combat;

import com.xy2407.nsukaddition.common.compat.TaczGunBridge;
import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenInventory;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * NPC 战斗 AI：目标锁定、近战（学习僵尸）、弓弩远程、TACZ 枪械与三击一格挡盾牌。
 */
public final class CitizenCombatService {
    private static final double ATTACK_RANGE = 48.0D;
    private static final double RANGED_STAND_DISTANCE = 47.0D;
    private static final double RANGED_LOSE_RANGE = 96.0D;
    private static final int MELEE_ATTACK_INTERVAL = 20;
    private static final int BOW_ATTACK_INTERVAL = 0;
    private static final int BOW_CHARGE_TICKS = 20;
    private static final int CROSSBOW_ATTACK_INTERVAL = 0;
    private static final int REVENGE_TTL_TICKS = 120;
    private static final int SHIELD_TRIGGER_HITS = 3;
    private static final int SHIELD_BLOCK_TICKS = 30;
    private static final int MOVE_REQUEST_INTERVAL = 5;
    private static final double ACTIVE_SCAN_RANGE = 32.0D;
    private static final int ACTIVE_SCAN_INTERVAL = 40;

    private static final ConcurrentMap<UUID, NpcCombatState> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Integer> SHIELD_HITS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Set<UUID>> COMMAND_TARGETS = new ConcurrentHashMap<>();

    private static final class NpcCombatState {
        @Nullable LivingEntity target;
        final java.util.Deque<UUID> commandTargetQueue = new java.util.ArrayDeque<>();
        int meleeCooldown;
        int rangedCooldown;
        boolean crossbowLoading;
        boolean gunDrawn;
        int lastMoveRequestTick = -1;
        long shieldBlockUntil = -1;
        int activeScanCooldown = 0;
        boolean hadMoveTask = false;
    }

    private CitizenCombatService() {
    }

    public static void tickCombat(ServerLevel level, CitizenEntity npc) {
        UUID id = npc.getUUID();
        if (!npc.isAlive() || npc.isRemoved()) {
            onNpcRemoved(id);
            return;
        }
        if (npc.isSleeping() || MedicalService.isHospitalized(level, id)) {
            stopCombat(level, npc, id);
            return;
        }
        boolean hasMoveTask = RtsCitizenTaskManager.hasActiveTask(id);
        RtsCitizenTaskManager.applyRtsSpeed(npc, hasMoveTask);
        NpcCombatState state = STATES.computeIfAbsent(id, k -> new NpcCombatState());
        if (state.rangedCooldown > 0) {
            state.rangedCooldown--;
        }
        if (state.hadMoveTask && !hasMoveTask) {
            state.activeScanCooldown = 0;
        }
        state.hadMoveTask = hasMoveTask;
        updateShieldState(level, npc, state);
        updateTarget(level, npc, state);
        LivingEntity target = state.target;
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            state.target = null;
            target = null;
        }
        if (RtsCitizenTaskManager.isFrozen(id) && !hasCommandTarget(id) && target == null) {
            stopCombat(level, npc, id);
            return;
        }
        if (target == null) {
            if (!hasCommandTarget(id)) {
                stopCombat(level, npc, id);
            }
            return;
        }
        if (state.shieldBlockUntil > level.getGameTime()) {
            faceTarget(npc, target);
            return;
        }
        syncWeaponsFromInventory(npc);
        boolean gunHeld = TaczGunBridge.isGunHeld(npc);
        ItemStack ranged = gunHeld ? ItemStack.EMPTY : getVanillaRangedWeapon(npc);
        boolean hasMelee = gunHeld || hasMeleeWeapon(npc);
        double dist = npc.distanceTo(target);
        if (gunHeld) {
            tickGun(level, npc, state, target, dist, hasMoveTask);
        } else if (!ranged.isEmpty()) {
            tickRanged(level, npc, state, target, ranged, dist, hasMoveTask);
        } else if (hasMelee) {
            tickMelee(level, npc, state, target, dist, hasMoveTask);
        }
    }

    private static void tickGun(ServerLevel level, CitizenEntity npc, NpcCombatState state, LivingEntity target,
                                double dist, boolean hasMoveTask) {
        if (!hasMoveTask) {
            faceTarget(npc, target);
        }
        if (dist <= ATTACK_RANGE) {
            if (npc.hasLineOfSight(target)) {
                tryGunShoot(npc, state, target);
            } else if (!hasMoveTask) {
                moveToTarget(level, npc, state, target);
            }
            return;
        }
        if (!hasMoveTask) {
            moveToRangePoint(level, npc, state, target);
        }
    }

    private static void tickRanged(ServerLevel level, CitizenEntity npc, NpcCombatState state, LivingEntity target,
                                   ItemStack ranged, double dist, boolean hasMoveTask) {
        if (!hasMoveTask) {
            faceTarget(npc, target);
        }
        if (dist > ATTACK_RANGE) {
            if (!hasMoveTask) {
                moveToRangePoint(level, npc, state, target);
            }
            return;
        }
        if (npc.hasLineOfSight(target) || hasMoveTask) {
            if (ranged.is(Items.BOW)) {
                tryBowAttack(level, npc, state, target, ranged);
            } else {
                tryCrossbowAttack(level, npc, state, target, ranged);
            }
        } else {
            moveToTarget(level, npc, state, target);
        }
    }

    private static void tickMelee(ServerLevel level, CitizenEntity npc, NpcCombatState state, LivingEntity target,
                                  double dist, boolean hasMoveTask) {
        if (!hasMoveTask) {
            faceTarget(npc, target);
        }
        tryMeleeAttack(npc, state, target);
        if (!hasMoveTask) {
            moveToTarget(level, npc, state, target);
        }
    }

    public static void onNpcHurt(CitizenEntity npc, DamageSource source) {
        if (npc.level().isClientSide() || npc.isSleeping()) {
            return;
        }
        InteractionHand shieldHand = getShieldHand(npc);
        if (shieldHand == null) {
            return;
        }
        int hits = SHIELD_HITS.merge(npc.getUUID(), 1, Integer::sum);
        if (hits < SHIELD_TRIGGER_HITS) {
            return;
        }
        SHIELD_HITS.put(npc.getUUID(), 0);
        NpcCombatState state = STATES.computeIfAbsent(npc.getUUID(), k -> new NpcCombatState());
        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            faceEntity(npc, attacker);
        }
        if (npc.isUsingItem()) {
            npc.stopUsingItem();
        }
        npc.startUsingItem(shieldHand);
        state.shieldBlockUntil = npc.level().getGameTime() + SHIELD_BLOCK_TICKS;
    }

    public static void onNpcRemoved(UUID id) {
        STATES.remove(id);
        SHIELD_HITS.remove(id);
        COMMAND_TARGETS.remove(id);
    }

    public static void setCommandTargets(UUID npcId, Set<UUID> targetIds) {
        if (npcId == null || targetIds == null || targetIds.isEmpty()) {
            return;
        }
        COMMAND_TARGETS.put(npcId, new java.util.LinkedHashSet<>(targetIds));
        NpcCombatState state = STATES.get(npcId);
        if (state != null) {
            state.commandTargetQueue.clear();
            state.commandTargetQueue.addAll(targetIds);
            state.target = null;
        }
    }

    public static boolean hasCommandTarget(UUID npcId) {
        return npcId != null && COMMAND_TARGETS.containsKey(npcId)
                && !COMMAND_TARGETS.get(npcId).isEmpty();
    }

    public static boolean isInCombat(UUID npcId) {
        if (npcId == null) return false;
        NpcCombatState state = STATES.get(npcId);
        return state != null && state.target != null;
    }

    public static LivingEntity getCombatTarget(UUID npcId) {
        if (npcId == null) return null;
        NpcCombatState state = STATES.get(npcId);
        return state != null ? state.target : null;
    }

    public static boolean isMeleeFighter(CitizenEntity npc) {
        if (npc == null) return true;
        if (TaczGunBridge.isGunHeld(npc)) return false;
        net.minecraft.world.item.Item item = npc.getMainHandItem().getItem();
        return !(item instanceof net.minecraft.world.item.BowItem)
                && !(item instanceof net.minecraft.world.item.CrossbowItem);
    }

    public static void clearCommandTarget(UUID npcId) {
        if (npcId == null) return;
        COMMAND_TARGETS.remove(npcId);
        NpcCombatState state = STATES.get(npcId);
        if (state != null) {
            state.commandTargetQueue.clear();
            state.target = null;
        }
    }

    public static void clearAllCommandTargets(ServerLevel level, ServerPlayer player) {
        for (net.minecraft.world.entity.Entity entity : level.getEntities().getAll()) {
            if (!(entity instanceof CitizenEntity citizen)) {
                continue;
            }
            if (!RtsCityAccessValidator.canControlNpc(level, player, citizen)) {
                continue;
            }
            COMMAND_TARGETS.remove(citizen.getUUID());
            STATES.remove(citizen.getUUID());
            citizen.setLastHurtByMob(null);
        }
    }

    private static void updateTarget(ServerLevel level, CitizenEntity npc, NpcCombatState state) {
        double loseRange = hasRangedWeapon(npc) ? RANGED_LOSE_RANGE : ATTACK_RANGE;
        UUID npcId = npc.getUUID();

        Set<UUID> commandSet = COMMAND_TARGETS.get(npcId);
        if (commandSet != null && !commandSet.isEmpty()) {
            if (state.commandTargetQueue.isEmpty() && state.target == null) {
                state.commandTargetQueue.addAll(commandSet);
            }
            java.util.Iterator<UUID> it = state.commandTargetQueue.iterator();
            while (it.hasNext()) {
                UUID tid = it.next();
                net.minecraft.world.entity.Entity entity = level.getEntity(tid);
                if (entity == null) continue;
                if (!(entity instanceof LivingEntity living) || !living.isAlive()
                        || !isValidTarget(npc, living)) {
                    it.remove();
                    commandSet.remove(tid);
                }
            }
            if (state.commandTargetQueue.isEmpty()) {
                COMMAND_TARGETS.remove(npcId);
                state.target = null;
            } else {
                LivingEntity closest = null;
                double closestDist = Double.MAX_VALUE;
                for (UUID tid : state.commandTargetQueue) {
                    net.minecraft.world.entity.Entity entity = level.getEntity(tid);
                    if (entity instanceof LivingEntity living && living.isAlive()) {
                        double d = npc.distanceToSqr(living);
                        if (d < closestDist) {
                            closestDist = d;
                            closest = living;
                        }
                    }
                }
                state.target = closest;
                return;
            }
        }

        LivingEntity attacker = npc.getLastHurtByMob();
        if (attacker != null && attacker.isAlive()
                && level.getGameTime() - npc.getLastHurtByMobTimestamp() < REVENGE_TTL_TICKS
                && isValidTarget(npc, attacker)) {
            state.target = attacker;
            return;
        }

        if (state.target != null) {
            LivingEntity t = state.target;
            if (!t.isAlive() || t.isRemoved()
                    || npc.distanceToSqr(t) > loseRange * loseRange
                    || (t instanceof Player p && (p.isSpectator() || p.isCreative()))) {
                state.target = null;
            } else {
                if (state.activeScanCooldown > 0) {
                    state.activeScanCooldown--;
                } else {
                    state.activeScanCooldown = ACTIVE_SCAN_INTERVAL;
                    LivingEntity nearest = scanNearbyEnemy(level, npc);
                    if (nearest != null && nearest != t) {
                        double currentDist = npc.distanceToSqr(t);
                        double nearestDist = npc.distanceToSqr(nearest);
                        if (nearestDist < currentDist - 16.0D) {
                            state.target = nearest;
                        }
                    }
                }
                return;
            }
        }

        if (state.activeScanCooldown > 0) {
            state.activeScanCooldown--;
        } else {
            state.activeScanCooldown = ACTIVE_SCAN_INTERVAL;
            LivingEntity scanned = scanNearbyEnemy(level, npc);
            if (scanned != null) {
                state.target = scanned;
            }
        }
    }

    @Nullable
    private static LivingEntity scanNearbyEnemy(ServerLevel level, CitizenEntity npc) {
        if (level == null || npc == null || !npc.isAlive()) return null;
        AABB box = npc.getBoundingBox().inflate(ACTIVE_SCAN_RANGE);
        List<LivingEntity> entities;
        try {
            entities = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box);
        } catch (Exception ignored) {
            return null;
        }
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity living : entities) {
            if (living == null || living == npc) continue;
            if (!living.isAlive() || living.isRemoved()) continue;
            if (!isValidTarget(npc, living)) continue;
            if (!(living instanceof net.minecraft.world.entity.monster.Enemy)) continue;
            double d;
            try {
                d = npc.distanceToSqr(living);
            } catch (Exception ignored) {
                continue;
            }
            if (d > ACTIVE_SCAN_RANGE * ACTIVE_SCAN_RANGE) continue;
            try {
                if (!npc.getSensing().hasLineOfSight(living)) continue;
            } catch (Exception ignored) {
                continue;
            }
            if (d < nearestDist) {
                nearestDist = d;
                nearest = living;
            }
        }
        return nearest;
    }

    private static boolean hasRangedWeapon(CitizenEntity npc) {
        if (TaczGunBridge.isGunHeld(npc)) {
            return true;
        }
        ItemStack main = npc.getMainHandItem();
        ItemStack off = npc.getOffhandItem();
        return main.is(Items.BOW) || main.is(Items.CROSSBOW)
                || off.is(Items.BOW) || off.is(Items.CROSSBOW);
    }

    private static boolean isValidTarget(CitizenEntity npc, LivingEntity t) {
        if (t == npc || !t.isAlive()) {
            return false;
        }
        if (t instanceof Player p) {
            return !p.isSpectator() && !p.isCreative();
        }
        return true;
    }

    private static void syncWeaponsFromInventory(CitizenEntity npc) {
        CitizenInventory inv = npc.getCitizenInventory();
        ItemStack main = inv.getItem(CitizenInventory.MAIN_HAND_SLOT);
        ItemStack off = inv.getItem(CitizenInventory.OFF_HAND_SLOT);
        if (isWeaponStack(main) && !ItemStack.isSameItemSameComponents(npc.getMainHandItem(), main)) {
            npc.setItemSlot(EquipmentSlot.MAINHAND, main);
        }
        if (isWeaponStack(off) && !ItemStack.isSameItemSameComponents(npc.getOffhandItem(), off)) {
            npc.setItemSlot(EquipmentSlot.OFFHAND, off);
        }
    }

    private static void tryMeleeAttack(CitizenEntity npc, NpcCombatState state, LivingEntity target) {
        state.meleeCooldown--;
        if (state.meleeCooldown <= 0 && npc.isWithinMeleeAttackRange(target) && npc.getSensing().hasLineOfSight(target)) {
            state.meleeCooldown = MELEE_ATTACK_INTERVAL;
            faceTarget(npc, target, false);
            npc.swing(InteractionHand.MAIN_HAND);
            npc.doHurtTarget(target);
        }
    }

    private static void tryBowAttack(ServerLevel level, CitizenEntity npc, NpcCombatState state,
                                     LivingEntity target, ItemStack bow) {
        InteractionHand hand = npc.getMainHandItem().is(Items.BOW) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!npc.isUsingItem()) {
            if (state.rangedCooldown <= 0) {
                npc.startUsingItem(hand);
            }
            return;
        }
        int ticks = npc.getTicksUsingItem();
        if (ticks >= BOW_CHARGE_TICKS) {
            npc.stopUsingItem();
            state.rangedCooldown = BOW_ATTACK_INTERVAL;
            if (npc.hasLineOfSight(target)) {
                faceTarget(npc, target, false);
                float power = BowItem.getPowerForTime(ticks);
                shootArrow(level, npc, target, bow, power, power * 3.0F);
            }
        } else if (ticks > 5 && !npc.hasLineOfSight(target)) {
            npc.stopUsingItem();
        }
    }

    private static void shootArrow(ServerLevel level, CitizenEntity npc, LivingEntity target,
                                   ItemStack weaponStack, float damageModifier, float speed) {
        ItemStack dummyArrow = new ItemStack(Items.ARROW);
        boolean infinity = EnchantmentHelper.processAmmoUse(level, weaponStack, dummyArrow, 1) == 0;

        ItemStack arrowStack;
        if (infinity) {
            arrowStack = dummyArrow;
        } else {
            Optional<ItemStack> arrowOpt = npc.getCitizenInventory().extractFirstBackpack(
                    stack -> stack.is(ItemTags.ARROWS));
            if (arrowOpt.isEmpty()) {
                return;
            }
            arrowStack = arrowOpt.get();
        }

        AbstractArrow arrow = ProjectileUtil.getMobArrow(npc, arrowStack, damageModifier, weaponStack);
        if (infinity) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }

        double d0 = target.getX() - npc.getX();
        double d2 = target.getZ() - npc.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        arrow.shoot(d0, dy + d3 * 0.1D, d2, speed, 1.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.SKELETON_SHOOT,
                npc.getSoundSource(), 1.0F, 1.0F / (npc.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    private static void tryCrossbowAttack(ServerLevel level, CitizenEntity npc, NpcCombatState state,
                                          LivingEntity target, ItemStack crossbow) {
        InteractionHand hand = npc.getMainHandItem().is(Items.CROSSBOW) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        if (state.crossbowLoading) {
            if (!npc.isUsingItem()) {
                state.crossbowLoading = false;
                return;
            }
            int ticks = npc.getTicksUsingItem();
            if (ticks >= CrossbowItem.getChargeDuration(crossbow, npc)) {
                npc.stopUsingItem();
                state.crossbowLoading = false;
                if (npc.hasLineOfSight(target)) {
                    faceTarget(npc, target, false);
                    state.rangedCooldown = CROSSBOW_ATTACK_INTERVAL;
                    shootArrow(level, npc, target, crossbow, 1.0F, 3.15F);
                }
            } else if (ticks > 5 && !npc.hasLineOfSight(target)) {
                npc.stopUsingItem();
                state.crossbowLoading = false;
            }
            return;
        }

        if (state.rangedCooldown <= 0) {
            npc.startUsingItem(hand);
            state.crossbowLoading = true;
        }
    }

    private static void tryGunShoot(CitizenEntity npc, NpcCombatState state, LivingEntity target) {
        InteractionHand hand = getGunHand(npc);
        if (hand == null) {
            state.gunDrawn = false;
            return;
        }
        ItemStack gun = npc.getItemInHand(hand);
        if (!state.gunDrawn) {
            TaczGunBridge.draw(npc, gun);
            state.gunDrawn = true;
            return;
        }
        double dx = target.getX() - npc.getX();
        double dy = target.getEyeY() - npc.getEyeY();
        double dz = target.getZ() - npc.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        String result = TaczGunBridge.shoot(npc, pitch, yaw);
        if ("NOT_DRAW".equals(result)) {
            TaczGunBridge.draw(npc, gun);
            state.gunDrawn = true;
        } else if ("NEED_BOLT".equals(result)) {
            TaczGunBridge.bolt(npc);
        } else if ("NO_AMMO".equals(result)) {
            TaczGunBridge.reload(npc);
        }
    }

    private static void stopCombat(ServerLevel level, CitizenEntity npc, UUID id) {
        NpcCombatState state = STATES.get(id);
        if (state == null) {
            return;
        }
        if (state.target != null || state.gunDrawn || state.shieldBlockUntil > 0 || npc.isUsingItem()) {
            state.target = null;
            state.gunDrawn = false;
            state.shieldBlockUntil = -1;
            if (npc.isUsingItem()) {
                npc.stopUsingItem();
            }
            CitizenData data = CitizenManager.get(level).getCitizen(id).orElse(null);
            if (data != null) {
                CitizenJobVisualService.sync(level, npc, data);
            }
        }
    }

    private static void updateShieldState(ServerLevel level, CitizenEntity npc, NpcCombatState state) {
        if (state.shieldBlockUntil < 0) {
            return;
        }
        if (level.getGameTime() >= state.shieldBlockUntil || getShieldHand(npc) == null) {
            if (npc.isUsingItem()) {
                npc.stopUsingItem();
            }
            state.shieldBlockUntil = -1;
        } else if (!npc.isUsingItem()) {
            InteractionHand hand = getShieldHand(npc);
            if (hand != null) {
                npc.startUsingItem(hand);
            }
        }
    }

    private static void moveToTarget(ServerLevel level, CitizenEntity npc, NpcCombatState state, LivingEntity target) {
        if (npc.tickCount - state.lastMoveRequestTick < MOVE_REQUEST_INTERVAL) {
            return;
        }
        state.lastMoveRequestTick = npc.tickCount;
        CitizenNavigationService.requestMove(level, npc.getUUID(), target.position(), MovementIntent.WALK);
    }

    private static void moveToRangePoint(ServerLevel level, CitizenEntity npc, NpcCombatState state, LivingEntity target) {
        if (npc.tickCount - state.lastMoveRequestTick < MOVE_REQUEST_INTERVAL) {
            return;
        }
        Vec3 toTarget = target.position().subtract(npc.position());
        if (toTarget.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 standPoint = target.position().add(toTarget.normalize().scale(RANGED_STAND_DISTANCE));
        state.lastMoveRequestTick = npc.tickCount;
        CitizenNavigationService.requestMove(level, npc.getUUID(), standPoint, MovementIntent.WALK);
    }

    private static void faceTarget(CitizenEntity npc, LivingEntity target) {
        faceEntity(npc, target, true);
    }

    private static void faceTarget(CitizenEntity npc, LivingEntity target, boolean includeBody) {
        faceEntity(npc, target, includeBody);
    }

    private static void faceEntity(CitizenEntity npc, LivingEntity target) {
        faceEntity(npc, target, true);
    }

    private static void faceEntity(CitizenEntity npc, LivingEntity target, boolean includeBody) {
        npc.lookAt(Anchor.EYES, target.getEyePosition());
        npc.yHeadRot = npc.getYRot();
        if (includeBody) {
            npc.yBodyRot = npc.getYRot();
        }
    }

    private static ItemStack getVanillaRangedWeapon(CitizenEntity npc) {
        ItemStack main = npc.getMainHandItem();
        if (main.is(Items.BOW) || main.is(Items.CROSSBOW)) {
            return main;
        }
        ItemStack off = npc.getOffhandItem();
        if (off.is(Items.BOW) || off.is(Items.CROSSBOW)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasMeleeWeapon(CitizenEntity npc) {
        return isMeleeWeaponStack(npc.getMainHandItem()) || isMeleeWeaponStack(npc.getOffhandItem());
    }

    private static boolean isWeaponStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return isMeleeWeaponStack(stack) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
    }

    private static boolean isMeleeWeaponStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (TaczGunBridge.isGunItem(stack)) {
            return true;
        }
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof DiggerItem || item instanceof TridentItem
                || item instanceof MaceItem;
    }

    @Nullable
    private static InteractionHand getShieldHand(CitizenEntity npc) {
        if (npc.getMainHandItem().is(Items.SHIELD)) {
            return InteractionHand.MAIN_HAND;
        }
        if (npc.getOffhandItem().is(Items.SHIELD)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    @Nullable
    private static InteractionHand getGunHand(CitizenEntity npc) {
        if (TaczGunBridge.isGunItem(npc.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (TaczGunBridge.isGunItem(npc.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.ItemHandler.ENTITY,
                common.cn.kafei.simukraft.registry.ModEntities.CITIZEN.get(),
                (citizen, ctx) -> new InvWrapper(citizen.getCitizenInventory()));
    }
}
