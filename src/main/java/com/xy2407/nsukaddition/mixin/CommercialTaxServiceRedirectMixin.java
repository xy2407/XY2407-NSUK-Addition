package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.server.city.VillageTourismService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.commercial.CommercialTaxService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 拦截collectDueTaxes方法本身：用游客/商队购物收入替代企业税收，住宅租金不受影响。 */
@Mixin(CommercialTaxService.class)
public abstract class CommercialTaxServiceRedirectMixin {

    @Inject(method = "collectDueTaxes(Lnet/minecraft/server/level/ServerLevel;J)Ljava/util/Map;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$replaceWithShopIncome(ServerLevel level, long day, CallbackInfoReturnable<Map<UUID, Double>> cir) {
        Map<UUID, Double> result = new HashMap<>();
        for (CityData city : CityService.allCities(level)) {
            double income = VillageTourismService.getTouristIncome(city.cityId());
            if (income > 0.0D) {
                result.put(city.cityId(), income);
            }
        }
        cir.setReturnValue(result);
    }
}
