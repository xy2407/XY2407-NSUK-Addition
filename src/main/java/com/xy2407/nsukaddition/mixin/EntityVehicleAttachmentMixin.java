package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 市民骑马锚点：getVehicleAttachmentPoint 是 Entity 的方法，在此覆盖市民+马场景，
 * 让 positionRider 直接把乘员放到马背高度，避免与每 tick setPos 竞争导致上下抽动。
 */
@Mixin(Entity.class)
public abstract class EntityVehicleAttachmentMixin {

    @Inject(method = "getVehicleAttachmentPoint", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukaddition$horseAttachment(Entity vehicle, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof CitizenEntity && vehicle instanceof AbstractHorse) {
            cir.setReturnValue(new Vec3(0.0, 0.5, 0.0));
        }
    }
}
