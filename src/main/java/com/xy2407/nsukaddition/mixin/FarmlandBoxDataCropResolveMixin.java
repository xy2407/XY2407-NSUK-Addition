package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.farmland.ModFarmCropRegistry;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在 fromId 方法开头优先从 CROP_MAP 查找自定义作物，避免依赖 values() 遍历。 */
@Mixin(FarmCrop.class)
public class FarmlandBoxDataCropResolveMixin {

    @Inject(method = "fromId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$resolveCustomCrop(String id,
                                                   CallbackInfoReturnable<FarmCrop> cir) {
        if (id == null || id.isBlank()) return;
        if (ModFarmCropRegistry.cropMapSize() == 0) return;
        FarmCrop crop = ModFarmCropRegistry.findById(id);
        if (crop != null) {
            cir.setReturnValue(crop);
        }
    }
}
