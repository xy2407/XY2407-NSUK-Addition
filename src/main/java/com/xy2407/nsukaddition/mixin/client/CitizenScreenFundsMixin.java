package com.xy2407.nsukaddition.mixin.client;

import client.cn.kafei.simukraft.client.citizen.CitizenInfoText;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 在NPC身份卡底部添加资金行（从statusLabel中的||分隔符解析资金数据）。 */
@Mixin(value = CitizenInfoText.class, remap = false)
public class CitizenScreenFundsMixin {

    private static final String FUNDS_SEPARATOR = "||";

    @Inject(method = "cardLines", at = @At("RETURN"), remap = false, cancellable = true)
    private static void nsuk$addFundsLine(String cardId, CitizenInfoResponsePacket packet,
                                           CallbackInfoReturnable<List<Component>> cir) {
        if ("residence".equals(cardId) || "work".equals(cardId)) return;
        String label = packet.statusLabel();
        if (label == null || !label.contains(FUNDS_SEPARATOR)) return;
        String funds = label.substring(label.indexOf(FUNDS_SEPARATOR) + FUNDS_SEPARATOR.length());
        List<Component> modified = new ArrayList<>(cir.getReturnValue());
        modified.add(Component.translatable("gui.xy2407_nsuk_addition.tourist.funds", funds));
        cir.setReturnValue(List.copyOf(modified));
    }
}
