package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.RentEntry;
import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.ResidentialRentService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 优化收租遍历：用POI_ID反向索引替代getPoiAt空间查询，消除M×K次getPoiAt调用。 */
@Mixin(ResidentialRentService.class)
public abstract class ResidentialRentOptimizationMixin {

    @Shadow
    private static double rentAmount(PlacedBuildingRecord building) { return 0.0D; }

    @Inject(method = "collectRentByCity", at = @At("HEAD"), cancellable = true)
    private static void nsuk$optimizedCollectRent(ServerLevel level, CallbackInfoReturnable<Map<UUID, Double>> cir) {
        PlacedBuildingService.ensureCityPoisRegistered(level);
        CityPoiManager poiManager = CityPoiManager.get(level);

        Map<UUID, RentEntry> poiToRent = new HashMap<>();
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (building.cityId() == null || !"residential".equalsIgnoreCase(building.category())) continue;
            double rent = rentAmount(building);
            if (rent <= 0.0D) continue;
            for (BuildingPoiInstance poi : building.poiInstances()) {
                if (poi.poiType() != CityPoiType.RESIDENTIAL) continue;
                try {
                    UUID poiId = UUID.fromString(poi.key());
                    poiToRent.put(poiId, new RentEntry(building.buildingId(), building.cityId(), rent));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (poiToRent.isEmpty()) {
            cir.setReturnValue(Map.of());
            return;
        }

        Set<UUID> processedBuildings = new HashSet<>();
        Map<UUID, Double> rentByCity = new HashMap<>();
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (citizen.dead() || citizen.homeId() == null || citizen.cityId() == null) continue;
            RentEntry entry = poiToRent.get(citizen.homeId());
            if (entry == null || !entry.cityId().equals(citizen.cityId())) continue;
            CityPoiData home = poiManager.getPoi(citizen.homeId());
            if (home == null || !home.active() || home.type() != CityPoiType.RESIDENTIAL) continue;
            if (!processedBuildings.add(entry.buildingId())) continue;
            rentByCity.merge(entry.cityId(), entry.rent(), Double::sum);
        }

        rentByCity.replaceAll((cityId, rent) -> EconomyService.normalizeAmount(rent));
        rentByCity.entrySet().removeIf(entry -> entry.getValue() <= 0.0D);
        cir.setReturnValue(rentByCity);
    }
}
