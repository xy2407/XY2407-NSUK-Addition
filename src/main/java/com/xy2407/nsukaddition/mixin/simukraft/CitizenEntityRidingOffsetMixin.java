package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 NPC 骑马：骑马时每 tick 开头清跳跃输入，防止市民导航/移动逻辑触发坐骑跳跃。
 * 骑乘高度由 EntityVehicleAttachmentMixin 覆盖 getVehicleAttachmentPoint 定位，避免与 positionRider 竞争抽动。
 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityRidingOffsetMixin {

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void nsukaddition$clearJumpOnMount(CallbackInfo ci) {
        CitizenEntity self = (CitizenEntity) (Object) this;
        if (self.isPassenger()) {
            self.setJumping(false);
        }
    }
}