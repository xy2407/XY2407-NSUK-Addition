package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抑制市民骑马时马/骑手的随机跳跃输入（骑乘移动由移动任务控制，不应随机跳跃）。
 * 骑乘高度已由 CitizenEntity.getVehicleAttachmentPoint 锚点覆盖，不再每 tick setPos。
 */
@Mixin(AbstractHorse.class)
public abstract class HorseRidingPositionMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void nsukaddition$suppressRiderJump(CallbackInfo ci) {
        AbstractHorse horse = (AbstractHorse) (Object) this;
        Entity rider = horse.getFirstPassenger();
        if (rider instanceof CitizenEntity) {
            horse.setJumping(false);
        }
    }
}