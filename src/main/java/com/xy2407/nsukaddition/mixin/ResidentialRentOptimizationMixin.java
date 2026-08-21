package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityProsperityCache;
import com.xy2407.nsukaddition.common.city.ProsperityLevel;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.ResidentialRentService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 繁荣度系统收租：从缓存读取繁荣度定等级，有房市民数乘等级税率。 */
@Mixin(ResidentialRentService.class)
public abstract class ResidentialRentOptimizationMixin {

    @Inject(method = "collectRentByCity", at = @At("HEAD"), cancellable = true)
    private static void nsuk$prosperityBasedRent(ServerLevel level, CallbackInfoReturnable<Map<UUID, Double>> cir) {
        PlacedBuildingService.ensureCityPoisRegistered(level);
        CityPoiManager poiManager = CityPoiManager.get(level);

        Map<UUID, Integer> housedCitizensByCity = new HashMap<>();
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (citizen.dead() || citizen.homeId() == null || citizen.cityId() == null) continue;
            CityPoiData home = poiManager.getPoi(citizen.homeId());
            if (home == null || !home.active() || home.type() != CityPoiType.RESIDENTIAL) continue;
            if (!citizen.cityId().equals(home.cityId())) continue;
            housedCitizensByCity.merge(citizen.cityId(), 1, Integer::sum);
        }

        Map<UUID, Double> rentByCity = new HashMap<>();
        for (CityData city : CityManager.get(level).allCities()) {
            UUID cityId = city.cityId();
            int housedCitizens = housedCitizensByCity.getOrDefault(cityId, 0);
            if (housedCitizens <= 0) continue;
            long prosperity = CityProsperityCache.getOrCalculate(level, cityId);
            ProsperityLevel prosperityLevel = ProsperityLevel.fromValue(prosperity);
            double rent = housedCitizens * prosperityLevel.dailyTaxPerCitizen();
            rentByCity.put(cityId, EconomyService.normalizeAmount(rent));
        }

        rentByCity.entrySet().removeIf(entry -> entry.getValue() <= 0.0D);
        cir.setReturnValue(rentByCity);
    }
}
