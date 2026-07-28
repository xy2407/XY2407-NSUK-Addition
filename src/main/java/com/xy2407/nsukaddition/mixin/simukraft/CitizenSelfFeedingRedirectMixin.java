package com.xy2407.nsukaddition.mixin.simukraft;

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
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 拦截 NPC 觅食逻辑，将饥饿 NPC 重定向到餐厅就餐，并完全禁用商店购买食物。 */
@Mixin(CitizenSelfFeedingService.class)
public class CitizenSelfFeedingRedirectMixin {

    @Inject(method = "isSelfFeeding", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$alsoCheckDining(ServerLevel level, UUID citizenId, CallbackInfoReturnable<Boolean> cir) {
        if (citizenId != null && RestaurantDiningService.isDining(citizenId)) {
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

        java.util.Map<BlockPos, Integer> occupiedCount = new java.util.HashMap<>();
        java.util.Set<UUID> restaurantWorkerIds = new java.util.HashSet<>();
        for (var data : manager.all()) {
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
            if (restaurantWorkerIds.contains(citizen.workplaceId())) continue;
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, cid);
            if (entity == null) continue;

            if (entity.isPassenger() && entity.getVehicle() instanceof SitEntity) {
                entity.stopRiding();
                entity.setNoAi(false);
                entity.getNavigation().stop();
                if (citizen.statusLabel() != null && citizen.statusLabel().startsWith("gui.xy2407_nsuk_addition.cooking.dining.")) {
                    citizen.setStatusLabel("");
                    CitizenService.save(level, cid);
                }
            }

            if (entity.getHungerValue() > 4.0D) continue;

            tryAssignToRestaurant(level, citizen, entity, manager, occupiedCount);
        }
        ci.cancel();
    }

    private static boolean tryAssignToRestaurant(ServerLevel level, CitizenData citizen, CitizenEntity entity,
                                                  RestaurantBoxManager manager,
                                                  java.util.Map<BlockPos, Integer> occupiedCount) {
        boolean isTourist = citizen.cityId() == null
                || com.xy2407.nsukaddition.common.city.TourismConstants.TOURIST_STATUS_LABEL.equals(citizen.statusLabel());
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
