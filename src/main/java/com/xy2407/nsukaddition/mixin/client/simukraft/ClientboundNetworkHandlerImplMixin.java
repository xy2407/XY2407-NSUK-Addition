package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.network.ClientboundNetworkHandlerImpl;
import com.xy2407.nsukaddition.client.city.OwnCityClientCache;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 捕获玩家自己的城市 id，供领地着色不受打开其它城市核心影响。 */
@Mixin(ClientboundNetworkHandlerImpl.class)
public abstract class ClientboundNetworkHandlerImplMixin {

    @Inject(method = "handleCityChunkSync", at = @At("HEAD"), remap = false)
    private void nsuk$captureOwnCity(CityChunkSyncPacket packet, CallbackInfo ci) {
        OwnCityClientCache.update(packet.currentCityId());
    }
}