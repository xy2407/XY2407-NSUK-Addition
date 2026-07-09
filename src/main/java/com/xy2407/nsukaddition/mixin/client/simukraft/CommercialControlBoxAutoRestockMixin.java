package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.commercial.CommercialControlBoxScreenOpener;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.xy2407.nsukaddition.client.autorestock.ClientAutoRestockCache;
import com.xy2407.nsukaddition.common.network.AutoRestockStatePacket;
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在商业控制箱 panel 右上角注入自动补货开关（标签+开/关按钮），按钮随状态切换。 */
@Mixin(CommercialControlBoxScreenOpener.class)
public abstract class CommercialControlBoxAutoRestockMixin {

    @Unique
    private static final int LBL_RIGHT = 20;
    @Unique
    private static final int LBL_TOP = 0;
    @Unique
    private static final int BTN_RIGHT = 20;
    @Unique
    private static final int BTN_TOP = 14;
    @Unique
    private static final int LBL_W = 82;
    @Unique
    private static final int LBL_H = 12;
    @Unique
    private static final int BTN_W = 40;
    @Unique
    private static final int BTN_H = 18;

    @Unique
    private static final ThreadLocal<CommercialControlBoxOpenResponsePacket> PACKET_HOLDER = new ThreadLocal<>();

    @Inject(method = "createUi", at = @At("HEAD"), remap = false)
    private static void capturePacket(CommercialControlBoxOpenResponsePacket packet,
                                      CallbackInfoReturnable<ModularUI> cir) {
        PACKET_HOLDER.set(packet);
    }

    @Inject(method = "createUi", at = @At("RETURN"), remap = false)
    private static void addRestockButtons(CallbackInfoReturnable<ModularUI> cir) {
        CommercialControlBoxOpenResponsePacket packet = PACKET_HOLDER.get();
        PACKET_HOLDER.remove();
        if (packet == null) return;
        BlockPos boxPos = packet.boxPos();
        if (boxPos == null) return;

        ModularUI modUi = cir.getReturnValue();
        if (modUi == null || modUi.ui == null) return;
        UIElement root = modUi.ui.rootElement;
        if (root == null) return;

        UIElement panel = null;
        for (UIElement child : root.getChildren()) {
            if (child.hasClass("simukraft_panel")) {
                panel = child;
                break;
            }
        }
        if (panel == null) return;

        PacketDistributor.sendToServer(new AutoRestockStatePacket(boxPos, false));

        boolean on = ClientAutoRestockCache.isEnabled(boxPos);

        Label lbl = new Label();
        lbl.setText(Component.translatable("gui.xy2407_nsuk_addition.autorestock.toggle"));
        lbl.setOverflowVisible(false);
        lbl.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(true)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.HIDE)
                .textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER)
                .textAlignVertical(com.lowdragmc.lowdraglib2.gui.ui.data.Vertical.CENTER));

        Button onBtn = new Button();
        onBtn.setText(Component.literal("开"));
        onBtn.setActive(!on);

        Button offBtn = new Button();
        offBtn.setText(Component.literal("关"));
        offBtn.setActive(on);

        onBtn.setOnClick(e -> {
            ClientAutoRestockCache.set(boxPos, true);
            PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, true));
            onBtn.setActive(false);
            offBtn.setActive(true);
        });
        offBtn.setOnClick(e -> {
            ClientAutoRestockCache.set(boxPos, false);
            PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, false));
            onBtn.setActive(true);
            offBtn.setActive(false);
        });

        lbl.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(LBL_RIGHT); l.top(LBL_TOP); l.width(LBL_W); l.height(LBL_H); });
        onBtn.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(BTN_RIGHT + BTN_W + 2); l.top(BTN_TOP); l.width(BTN_W); l.height(BTN_H); });
        offBtn.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(BTN_RIGHT); l.top(BTN_TOP); l.width(BTN_W); l.height(BTN_H); });

        panel.addChild(lbl);
        panel.addChild(onBtn);
        panel.addChild(offBtn);
    }
}
