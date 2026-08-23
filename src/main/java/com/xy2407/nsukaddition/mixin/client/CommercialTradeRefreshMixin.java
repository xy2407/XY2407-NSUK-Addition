package com.xy2407.nsukaddition.mixin.client;

import com.xy2407.nsukaddition.common.network.CommercialTradeRefreshRequestPacket;
import common.cn.kafei.simukraft.commercial.CommercialTradeUiRoot;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 交易时库存实时刷新：SimKraft 商店/商队交易原版仅在重新进入时才刷新库存显示。
 * 这里在每次交易后请求服务端重新推送交易视图，使库存数量即时更新。
 */
@Mixin(value = CommercialTradeUiRoot.class, remap = false)
public abstract class CommercialTradeRefreshMixin {

    @Shadow(remap = false)
    private CommercialTradeOpenResponsePacket packet;

    @Inject(method = "tradeSelected", at = @At("TAIL"), remap = false)
    private void nsuk$refreshAfterTrade(boolean quickMove, int count, CallbackInfo ci) {
        if (packet != null && packet.workerId() != null) {
            PacketDistributor.sendToServer(new CommercialTradeRefreshRequestPacket(packet.boxPos(), packet.workerId()));
        }
    }
}