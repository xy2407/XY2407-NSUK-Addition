package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityProsperityCache;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** 住宅建筑注册/注销时失效对应城市的繁荣度缓存。 */
@Mixin(PlacedBuildingService.class)
public abstract class PlacedBuildingServiceProsperityMixin {

    @Inject(method = "register", at = @At("RETURN"))
    private static void nsuk$onRegister(ServerLevel level, PlacedBuildingRecord record, CallbackInfo ci) {
        if (record.cityId() != null && "residential".equalsIgnoreCase(record.category())) {
            CityProsperityCache.invalidate(record.cityId());
        }
    }

    @Inject(method = "unregister", at = @At("HEAD"))
    private static void nsuk$onUnregister(ServerLevel level, UUID buildingId, CallbackInfo ci) {
        for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
            if (buildingId.equals(rec.buildingId())) {
                if (rec.cityId() != null && "residential".equalsIgnoreCase(rec.category())) {
                    CityProsperityCache.invalidate(rec.cityId());
                }
                break;
            }
        }
    }
}
