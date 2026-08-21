package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.index.CitizenWorkplaceIndex;
import common.cn.kafei.simukraft.citizen.CitizenData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 绑定变更时增量维护 workplaceId 反向索引（setWorkplaceId 是雇佣/解雇/转岗唯一入口）。
 * 索引本体存放在 CitizenWorkplaceIndex 普通类中，本 Mixin 只负责桥接，避免 Mixin 规范限制。
 */
@Mixin(value = CitizenData.class, remap = false)
public abstract class CitizenWorkplaceIndexMixin {

    @Shadow private UUID workplaceId;
    @Shadow private String dimensionId;
    @Shadow private UUID uuid;

    @Unique
    private UUID nsuk$oldWorkplaceId;
    @Unique
    private String nsuk$oldDimension;

    @Inject(method = "setWorkplaceId", at = @At("HEAD"), remap = false)
    private void nsuk$beforeSetWorkplaceId(UUID newWorkplaceId, CallbackInfo ci) {
        nsuk$oldWorkplaceId = workplaceId;
        nsuk$oldDimension = dimensionId;
    }

    @Inject(method = "setWorkplaceId", at = @At("RETURN"), remap = false)
    private void nsuk$afterSetWorkplaceId(UUID newWorkplaceId, CallbackInfo ci) {
        if (nsuk$oldWorkplaceId != null) {
            CitizenWorkplaceIndex.indexRemove(nsuk$oldDimension, nsuk$oldWorkplaceId, uuid);
        }
        if (newWorkplaceId != null) {
            CitizenWorkplaceIndex.indexPut(dimensionId, newWorkplaceId, uuid);
        }
        nsuk$oldWorkplaceId = null;
        nsuk$oldDimension = null;
    }
}
