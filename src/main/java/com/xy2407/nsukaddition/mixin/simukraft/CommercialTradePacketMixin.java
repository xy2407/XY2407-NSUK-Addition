package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.city.VillageTourismService;
import common.cn.kafei.simukraft.network.commercial.CommercialTradePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 商队交易购买：商队 leader 走商队专用执行，跳过 simukraft 商业控制箱的距离/绑定校验。 */
@Mixin(CommercialTradePacket.class)
public abstract class CommercialTradePacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsukaddition$caravanTrade(CommercialTradePacket packet, IPayloadContext context, CallbackInfo ci) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (VillageTourismService.isCaravanLeader(level, packet.workerId())) {
                boolean success = VillageTourismService.executeCaravanTrade(
                        level, player, packet.workerId(), packet.offerId(), packet.count());
                player.displayClientMessage(
                        Component.literal(success ? "交易成功" : "交易失败"),
                        true);
                ci.cancel();
            }
        }
    }
}
