package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.SimuKraftCityActivation;
import common.cn.kafei.simukraft.citizen.NpcPregnancyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 怀孕判孕仅在存在激活城市时执行,避免未加载城市的市民被写库。
 */
@Mixin(value = NpcPregnancyService.class, remap = false)
public abstract class NpcPregnancyServiceMixin {

    @Inject(method = "tickPregnancies", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void nsuk$skipWhenNoActiveCity(ServerLevel level, RandomSource random, long currentDay, CallbackInfo ci) {
        if (!SimuKraftCityActivation.hasActiveCity(level)) {
            ci.cancel();
        }
    }
}