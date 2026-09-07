package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import com.xy2407.nsukaddition.server.city.DialogNpcDialogService;
import com.xy2407.nsukaddition.server.city.VillageTourismService;
import common.cn.kafei.simukraft.network.commercial.CommercialTradePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 商队与初始物资商店交易分流：拦截 simukraft 商业交易包，按 offerId 前缀路由到对应服务，跳过控制箱校验。 */
@Mixin(CommercialTradePacket.class)
public abstract class CommercialTradePacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsukaddition$caravanTrade(CommercialTradePacket packet, IPayloadContext context, CallbackInfo ci) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            String offerId = packet.offerId();
            if (offerId != null && offerId.startsWith("dshop_")) {
                // 初始物资商店：由世界管家实体执行购买。
                Entity entity = level.getEntity(packet.workerId());
                if (entity instanceof DialogNpcEntity dialog) {
                    boolean success = DialogNpcDialogService.executeInitialShopBuy(
                            level, player, dialog, offerId.substring("dshop_".length()), packet.count());
                    player.displayClientMessage(
                            Component.literal(success ? "购买成功" : "购买失败"),
                            true);
                }
                ci.cancel();
                return;
            }
            if (VillageTourismService.isCaravanLeader(level, packet.workerId())) {
                boolean success = VillageTourismService.executeCaravanTrade(
                        level, player, packet.workerId(), offerId, packet.count());
                player.displayClientMessage(
                        Component.literal(success ? "交易成功" : "交易失败"),
                        true);
                ci.cancel();
            }
        }
    }
}
