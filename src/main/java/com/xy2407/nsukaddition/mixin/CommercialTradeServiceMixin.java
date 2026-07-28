package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.commercial.CommercialTaxService;
import common.cn.kafei.simukraft.commercial.CommercialTradeService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/** 屏蔽普通NPC购买时的收入记录，仅游客/商队通过TOURIST_INCOME渠道产生收入。 */
@Mixin(CommercialTradeService.class)
public class CommercialTradeServiceMixin {

    @Redirect(
            method = "executeNpcOffer",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/commercial/CommercialTaxService;recordShopIncome(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;D)V"),
            remap = false
    )
    private static void nsuk$disableNpcIncome(ServerLevel level, UUID cityId, double amount) {
    }
}
