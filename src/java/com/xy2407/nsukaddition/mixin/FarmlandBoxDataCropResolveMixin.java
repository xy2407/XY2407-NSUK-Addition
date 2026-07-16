package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.farmland.ModFarmCropRegistry;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 当FarmCrop.fromId()对自定义作物返回null时，从备份映射回退查找。 */
@Mixin(FarmCrop.class)
public class FarmlandBoxDataCropResolveMixin {

    @Inject(method = "fromId", at = @At("RETURN"), cancellable = true, remap = false)
    private static void xy2407$fallbackCustomCrop(String id,
                                                    CallbackInfoReturnable<FarmCrop> cir) {
        if (cir.getReturnValue() != null) return;
        FarmCrop crop = ModFarmCropRegistry.findById(id);
        if (crop != null) {
            cir.setReturnValue(crop);
        }
    }
}
