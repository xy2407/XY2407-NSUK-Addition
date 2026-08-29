package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.city.CityPopulationStats;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 人口统计守卫：住宅POI区块未FULL加载时跳过getBlockState，避免server tick强制加载完整区块触发实体重复追踪。 */
@Mixin(value = CityPopulationStats.class, remap = false)
public class CityPopulationStatsMixin {

    @Inject(method = "isLiveResidentialPoi", at = @At("HEAD"), cancellable = true)
    private static void nsuk$skipUnloadedPoi(ServerLevel level, BuildingPoiInstance poi, CallbackInfoReturnable<Boolean> cir) {
        if (poi.worldPos() == null || !level.isLoaded(poi.worldPos())) {
            cir.setReturnValue(false);
        }
    }
}