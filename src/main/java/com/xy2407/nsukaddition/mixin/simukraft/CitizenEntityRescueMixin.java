package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.common.cooking.RestaurantDiningService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** 拦截卡墙救援逻辑，NPC 就餐骑乘 SitEntity 期间跳过救援，避免传送粒子循环。 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityRescueMixin {

    @Inject(method = "rescueFromWall", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$skipRescueWhileDining(boolean immediate, CallbackInfo ci) {
        CitizenEntity self = (CitizenEntity) (Object) this;
        UUID id = self.getUUID();
        if (RestaurantDiningService.isDining(id)) {
            ci.cancel();
        }
    }
}
