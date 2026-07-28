package com.xy2407.nsukaddition.mixin.client;

import client.cn.kafei.simukraft.client.citizen.CitizenScreenOpener;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在NPC身份卡底部添加资金行（从statusLabel中的||分隔符解析资金数据）。 */
@Mixin(value = CitizenScreenOpener.class, remap = false)
public class CitizenScreenFundsMixin {

    private static final String FUNDS_SEPARATOR = "||";

    @Inject(method = "identityPanel", at = @At("RETURN"), remap = false)
    private static void nsuk$addFundsLine(CitizenInfoResponsePacket packet, CallbackInfoReturnable<UIElement> cir) {
        String label = packet.statusLabel();
        if (label == null || !label.contains(FUNDS_SEPARATOR)) return;

        String funds = label.substring(label.indexOf(FUNDS_SEPARATOR) + FUNDS_SEPARATOR.length());
        UIElement panel = cir.getReturnValue();
        UIElement wrapper = new UIElement();
        wrapper.layout(layout -> {
            layout.height(13);
            layout.widthPercent(100);
        });
        wrapper.style(style -> style.backgroundTexture(new TextTexture(
                Component.translatable("gui.xy2407_nsuk_addition.tourist.funds", funds).getString())
                .setType(TextTexture.TextType.LEFT)
                .setWidth(200)));
        panel.addChild(wrapper);
    }
}
