package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.TouristNpcHelper;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenWanderService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 游客/商队禁止随机漫游：其移动完全由旅游/交易流程控制（去商店、跟随队形），
 * 避免被随机漫游服务选中后乱走；村庄城市 NPC 保留低频漫游。
 */
@Mixin(CitizenWanderService.class)
public class CitizenWanderServiceMixin {

    @Inject(method = "canAutoWander", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$skipStationaryWander(ServerLevel level, CitizenEntity citizen,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (TouristNpcHelper.isStationary(citizen)) {
            cir.setReturnValue(false);
        }
    }
}
