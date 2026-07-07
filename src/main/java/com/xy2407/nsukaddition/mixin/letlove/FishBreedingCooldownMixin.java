package com.xy2407.nsukaddition.mixin.letlove;

import com.chinaex123.letfishlove.capabilities.FishBreedingCap;
import com.chinaex123.letfishlove.entity.FishBreedingUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 修改 FishBreedingUtil，将鱼类繁殖冷却重定向为固定一万二千刻。 */
@Mixin(FishBreedingUtil.class)
public abstract class FishBreedingCooldownMixin {

    @Redirect(method = "spawnFishFromBreeding", at = @At(value = "INVOKE",
            target = "Lcom/chinaex123/letfishlove/capabilities/FishBreedingCap;setCanLoveCooldown(IZ)V"),
            remap = false)
    private static void redirectSetCanLoveCooldown(FishBreedingCap cap, int cooldown, boolean sync) {
        cap.setCanLoveCooldown(12000, sync);
    }
}
