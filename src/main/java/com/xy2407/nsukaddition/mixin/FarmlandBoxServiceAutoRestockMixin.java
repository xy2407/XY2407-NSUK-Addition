package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 农田盒移除时清理自动补货状态，避免残留。 */
@Mixin(FarmlandBoxService.class)
public abstract class FarmlandBoxServiceAutoRestockMixin {

    @Inject(method = "onRemoved", at = @At("TAIL"), remap = false)
    private static void nsuk$clearAutoRestock(ServerLevel level, BlockPos boxPos, CallbackInfo ci) {
        AutoRestockConfig.remove(level, boxPos);
    }
}
