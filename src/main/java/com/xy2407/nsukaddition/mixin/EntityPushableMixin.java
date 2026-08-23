package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.rts.RtsPlayerState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 驱动期间玩家实体不参与物理推挤，避免与假人互相顶开。 */
@Mixin(Entity.class)
public abstract class EntityPushableMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$rtsNoPush(CallbackInfoReturnable<Boolean> cir) {
        Entity self = ((Entity) (Object) this);
        if (!self.level().isClientSide() && RtsPlayerState.isActive(self.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}