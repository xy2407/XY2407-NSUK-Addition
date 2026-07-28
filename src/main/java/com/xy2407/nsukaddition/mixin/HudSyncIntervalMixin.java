package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.network.hud.HudSyncService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** HUD同步间隔从20tick（1秒）提升到100tick（5秒），降低80%同步开销。 */
@Mixin(HudSyncService.class)
public class HudSyncIntervalMixin {

    @ModifyConstant(method = "tick", constant = @Constant(longValue = 20L), remap = false)
    private static long nsuk$longerInterval(long original) {
        return 100L;
    }
}
