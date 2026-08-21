package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.SimuKraftCityActivation;
import common.cn.kafei.simukraft.citizen.NpcChildbirthService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 新生市民出生仅在存在激活城市时执行,避免在未加载区块 spawn 实体引发 UUID 冲突。
 */
@Mixin(value = NpcChildbirthService.class, remap = false)
public abstract class NpcChildbirthServiceMixin {

    @Inject(method = "tickChildbirths", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void nsuk$skipWhenNoActiveCity(ServerLevel level, RandomSource random, long currentDay, CallbackInfo ci) {
        if (!SimuKraftCityActivation.hasActiveCity(level)) {
            ci.cancel();
        }
    }
}