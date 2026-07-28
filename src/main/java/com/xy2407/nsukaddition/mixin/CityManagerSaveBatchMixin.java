package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CitySaveBatchContext;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 拦截 saveCityIncremental：批处理模式下跳过写入，仅记录待保存城市ID。 */
@Mixin(CityManager.class)
public abstract class CityManagerSaveBatchMixin {

    @Inject(method = "saveCityIncremental", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$skipInBatchMode(CityData city, CallbackInfo ci) {
        if (CitySaveBatchContext.isInBatchMode()) {
            CitySaveBatchContext.markPending(city.cityId());
            ci.cancel();
        }
    }
}
