package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.TouristNpcHelper;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinition;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.cooking.RestaurantDiningService;
import com.xy2407.nsukaddition.common.entity.SitEntity;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenInventory;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 拦截 NPC 觅食逻辑，将饥饿 NPC 重定向到餐厅就餐，对接 2.1.0 罢工状态标签。 */
@Mixin(CitizenSelfFeedingService.class)
public class CitizenSelfFeedingRedirectMixin {

    private static final double HUNGER_START_THRESHOLD = 5.0D;
    private static final double HUNGER_STRIKE_THRESHOLD = 0.0D;

    @Inject(method = "isSelfFeeding", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$alsoCheckDining(ServerLevel level, UUID citizenId, CallbackInfoReturnable<Boolean> cir) {
        if (citizenId != null && RestaurantDiningService.isDining(citizenId)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isOnHungerStrike", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$reportStrikeWhenStarving(ServerLevel level, UUID citizenId, CallbackInfoReturnable<Boolean> cir) {
        if (level == null || citizenId == null) return;
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (entity == null) return;
        if (entity.getHungerValue() <= HUNGER_STRIKE_THRESHOLD
                && !RestaurantDiningService.isDining(citizenId)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$redirectToRestaurant(ServerLevel level, CallbackInfo ci) {
        if (level == null || level.isClientSide()) return;
        long gameTime = level.getGameTime();
        if (gameTime % 20L != 0L) return;

        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        CitizenManager cm = CitizenManager.get(level);

        Map<BlockPos, Integer> occupiedCount = new HashMap<>();
        Set<UUID> restaurantWorkerIds = new HashSet<>();
        for (RestaurantBoxData data : manager.all()) {
            occupiedCount.put(data.boxPos().immutable(), data.occupiedSeatCount());
            restaurantWorkerIds.add(CitizenEmploymentService.workplaceId(
                    RestaurantConstants.HIRE_SOURCE_TYPE, RestaurantConstants.HIRE_ROLE_CHEF, data.boxPos()));
            restaurantWorkerIds.add(CitizenEmploymentService.workplaceId(
                    RestaurantConstants.HIRE_SOURCE_TYPE, RestaurantConstants.HIRE_ROLE_WAITER, data.boxPos()));
        }

        for (CitizenData citizen : cm.allCitizens()) {
            UUID cid = citizen.uuid();
            if (RestaurantDiningService.isDining(cid)) continue;
            if (citizen.dead() || citizen.child()) continue;
            if (citizen.workStatusType() == CitizenWorkStatus.RESTING) continue;
            if (citizen.workStatusType() == CitizenWorkStatus.DEAD) continue;
            if (restaurantWorkerIds.contains(citizen.workplaceId())) continue;
            if (common.cn.kafei.simukraft.medical.MedicalMealService.isDoctorMealRunActive(level, cid)) continue;
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, cid);
            if (entity == null) continue;
            if (entity.isStayInPlace() || entity.getFollowPlayerId() != null
                    || com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager.isFrozen(cid)) continue;
            if (TouristNpcHelper.isCaravanEntity(entity)) continue;

            if (entity.isPassenger() && entity.getVehicle() instanceof SitEntity) {
                entity.stopRiding();
                entity.setNoAi(false);
                entity.getNavigation().stop();
                if (citizen.statusLabel() != null && citizen.statusLabel().startsWith("gui.xy2407_nsuk_addition.cooking.dining.")) {
                    citizen.setStatusLabel("");
                    CitizenService.save(level, cid);
                }
            }

            double hunger = entity.getHungerValue();
            if (hunger > HUNGER_START_THRESHOLD) {
                if (citizen.statusLabel() != null
                        && (CitizenSelfFeedingService.TOO_HUNGRY_STRIKE_STATUS.equals(citizen.statusLabel())
                        || CitizenSelfFeedingService.isSelfFeedingStatusLabel(citizen.statusLabel()))) {
                    citizen.setStatusLabel("");
                    CitizenService.save(level, cid);
                }
                continue;
            }

            if (tryEatFromInventory(level, entity)) {
                continue;
            }

            boolean assigned = tryAssignToRestaurant(level, citizen, entity, manager, occupiedCount);
            if (!assigned) {
                updateStrikeStatus(citizen, hunger, level, cid);
            } else if (CitizenSelfFeedingService.TOO_HUNGRY_STRIKE_STATUS.equals(citizen.statusLabel())) {
                citizen.setStatusLabel("");
                CitizenService.save(level, cid);
            }
        }
        ci.cancel();
    }

    private static boolean tryEatFromInventory(ServerLevel level, CitizenEntity entity) {
        if (entity == null) {
            return false;
        }
        CitizenInventory inventory = entity.getCitizenInventory();
        if (inventory == null) {
            return false;
        }
        Optional<ItemStack> foodOpt = inventory.extractFirstBackpack(stack -> {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            return stack.getFoodProperties(entity) != null;
        });
        if (foodOpt.isEmpty()) {
            return false;
        }
        ItemStack food = foodOpt.get();
        int nutrition = 1;
        FoodProperties properties = food.getFoodProperties(entity);
        if (properties != null) {
            nutrition = properties.nutrition();
        }
        double newHunger = Math.min(CitizenEntity.DEFAULT_HUNGER, entity.getHungerValue() + nutrition);
        entity.setHunger(newHunger);
        entity.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
        return true;
    }

    private static void updateStrikeStatus(CitizenData citizen, double hunger, ServerLevel level, UUID cid) {
        boolean shouldStrike = hunger <= HUNGER_STRIKE_THRESHOLD;
        boolean currentlyStriking = CitizenSelfFeedingService.TOO_HUNGRY_STRIKE_STATUS.equals(citizen.statusLabel());
        if (shouldStrike && !currentlyStriking) {
            citizen.setStatusLabel(CitizenSelfFeedingService.TOO_HUNGRY_STRIKE_STATUS);
            CitizenService.save(level, cid);
        } else if (!shouldStrike && currentlyStriking) {
            citizen.setStatusLabel("");
            CitizenService.save(level, cid);
        }
    }

    private static boolean tryAssignToRestaurant(ServerLevel level, CitizenData citizen, CitizenEntity entity,
                                                  RestaurantBoxManager manager,
                                                  Map<BlockPos, Integer> occupiedCount) {
        boolean isTourist = TouristNpcHelper.isTouristEntity(entity);
        if (isTourist && !com.xy2407.nsukaddition.server.city.VillageTourismService.canTouristAffordCheapestMeal(level, citizen.uuid())) {
            return false;
        }

        var npcPos = entity.blockPosition();

        for (RestaurantBoxData data : manager.all()) {
            if (!data.running()) continue;
            if (!npcPos.closerThan(data.boxPos(), 256.0D)) continue;

            PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
            if (building == null) continue;
            RestaurantDefinitionLoader.LoadResult loadResult = RestaurantDefinitionLoader.loadForBuilding(building);
            RestaurantDefinition definition = loadResult.definition();
            if (!loadResult.valid() || definition == null || definition.recipes().isEmpty()) continue;
            if (data.selectedCookItems().isEmpty()) continue;

            int totalSeats = definition.allSeatPositions().size();
            if (totalSeats <= 0) continue;
            int occupied = occupiedCount.getOrDefault(data.boxPos().immutable(), 0);
            if (occupied >= totalSeats) continue;

            if (RestaurantDiningService.startDining(level, citizen, building, definition, data)) {
                occupiedCount.put(data.boxPos().immutable(), occupied + 1);
                return true;
            }
        }
        return false;
    }
}