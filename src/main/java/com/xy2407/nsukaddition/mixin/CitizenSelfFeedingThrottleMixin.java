package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 每40tick才执行一次SelfFeeding遍历，降低50%市民遍历开销。 */
@Mixin(CitizenSelfFeedingService.class)
public class CitizenSelfFeedingThrottleMixin {

    private static final long NSUK_INTERVAL = 40L;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$throttle(ServerLevel level, CallbackInfo ci) {
        if (level == null || level.isClientSide()) {
            ci.cancel();
            return;
        }
        if (level.getGameTime() % NSUK_INTERVAL != 0L) {
            ci.cancel();
        }
    }
}
