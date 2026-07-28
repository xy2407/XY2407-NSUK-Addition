package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CitySaveBatchContext;
import common.cn.kafei.simukraft.economy.ResidentialRentService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** collectRentForDay 批处理：开启批处理模式跳过逐次写入，结束后统一保存，将4K次SQLite写入降为K次。 */
@Mixin(ResidentialRentService.class)
public abstract class ResidentialRentServiceBatchMixin {

    @Inject(method = "collectRentForDay(Lnet/minecraft/server/level/ServerLevel;J)V", at = @At("HEAD"), remap = false)
    private static void nsuk$startBatch(ServerLevel level, long day, CallbackInfo ci) {
        CitySaveBatchContext.startBatch();
    }

    @Inject(method = "collectRentForDay(Lnet/minecraft/server/level/ServerLevel;J)V", at = @At("RETURN"), remap = false)
    private static void nsuk$endBatch(ServerLevel level, long day, CallbackInfo ci) {
        CitySaveBatchContext.endBatch(level);
    }
}
