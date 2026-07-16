package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.farmland.FarmlandBoxScreenOpener;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.xy2407.nsukaddition.client.autorestock.ClientAutoRestockCache;
import com.xy2407.nsukaddition.common.network.AutoRestockStatePacket;
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在农田工作盒 panel 右上角注入自动补货入库开关（标签+开/关按钮），按钮随状态切换。 */
@Mixin(FarmlandBoxScreenOpener.class)
public abstract class FarmlandBoxAutoRestockMixin {

    @Unique
    private static final ThreadLocal<FarmlandBoxOpenResponsePacket> PACKET_HOLDER = new ThreadLocal<>();

    @Inject(method = "createUi", at = @At("HEAD"), remap = false)
    private static void capturePacket(FarmlandBoxOpenResponsePacket packet,
                                      CallbackInfoReturnable<ModularUI> cir) {
        PACKET_HOLDER.set(packet);
    }

    @Inject(method = "createUi", at = @At("RETURN"), remap = false)
    private static void addRestockButtons(CallbackInfoReturnable<ModularUI> cir) {
        FarmlandBoxOpenResponsePacket packet = PACKET_HOLDER.get();
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

        int btnW = 40;
        int btnH = 18;
        int lblH = 12;

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

        ClientAutoRestockCache.StateChangeListener listener = new ClientAutoRestockCache.StateChangeListener() {
            volatile boolean alive = true;
            @Override
            public void onAutoRestockStateChanged(BlockPos pos, boolean enabled) {
                if (!alive) return;
                if (pos.equals(boxPos.immutable())) {
                    onBtn.setActive(!enabled);
                    offBtn.setActive(enabled);
                }
            }
        };
        ClientAutoRestockCache.addListener(listener);

        lbl.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(0); l.top(0); l.width(btnW * 2 + 2); l.height(lblH); });
        onBtn.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(btnW + 2); l.top(lblH); l.width(btnW); l.height(btnH); });
        offBtn.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(0); l.top(lblH); l.width(btnW); l.height(btnH); });

        panel.addChild(lbl);
        panel.addChild(onBtn);
        panel.addChild(offBtn);
    }
}
