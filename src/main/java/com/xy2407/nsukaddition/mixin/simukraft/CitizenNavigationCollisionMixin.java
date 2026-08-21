package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 寻路期间取消市民之间的原版推挤向量。 */
@Mixin(Entity.class)
public abstract class CitizenNavigationCollisionMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void nsuk$cancelCitizenNavigationPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof CitizenEntity selfCitizen) || !(other instanceof CitizenEntity otherCitizen)) {
            return;
        }
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        if (CitizenNavigationService.isNavigating(level, selfCitizen.getUUID())
                || CitizenNavigationService.isNavigating(level, otherCitizen.getUUID())) {
            ci.cancel();
        }
    }
}
