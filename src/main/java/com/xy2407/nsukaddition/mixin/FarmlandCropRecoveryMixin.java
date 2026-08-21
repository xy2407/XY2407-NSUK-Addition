package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.farmland.ModFarmCropRegistry;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxManager;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 每600tick检查农田盒crop是否为null但全局映射中有保留的作物ID，从CROP_MAP恢复并持久化。 */
@Mixin(FarmlandFarmingService.class)
public class FarmlandCropRecoveryMixin {

    private static long lastRecoveryTick = 0;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void xy2407$recoverMissingCrops(ServerLevel level, CallbackInfo ci) {
        if (level == null || level.isClientSide()) return;
        long now = level.getGameTime();
        if (now - lastRecoveryTick < 600L) return;
        lastRecoveryTick = now;
        if (ModFarmCropRegistry.cropMapSize() == 0) return;

        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        for (FarmlandBoxData data : manager.all()) {
            if (data.crop() != null) continue;
            String cropId = ModFarmCropRegistry.getPreservedCropId(data.boxPos());
            if (cropId == null || cropId.isBlank()) continue;
            FarmCrop crop = ModFarmCropRegistry.findById(cropId);
            if (crop != null) {
                data.setCrop(crop);
                manager.persist(data);
            }
        }
    }
}
