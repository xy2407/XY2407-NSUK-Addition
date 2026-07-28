package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.citizen.CitizenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 将 NPC 饥饿衰减间隔减半（7200→3600 tick），使 NPC 更快感到饥饿。 */
@Mixin(CitizenManager.class)
public class CitizenManagerHungerMixin {

    @ModifyConstant(method = "tickCitizenData", constant = @Constant(longValue = 7200L), remap = false)
    private static long nsuk$fasterHungerDecay(long original) {
        return 3600L;
    }
}
