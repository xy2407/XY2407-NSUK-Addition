package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** CitizenHomeRestService节流优化：夜间休息40→60tick，恢复状态20→40tick，重试导航100→120tick。 */
@Mixin(CitizenHomeRestService.class)
public class CitizenHomeRestServiceThrottleMixin {

    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(longValue = 20L), remap = false)
    private static long nsuk$restoreInterval(long original) {
        return 40L;
    }

    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(longValue = 100L), remap = false)
    private static long nsuk$navInterval(long original) {
        return 120L;
    }

    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(longValue = 40L), remap = false)
    private static long nsuk$restInterval(long original) {
        return 60L;
    }
}
