package com.xy2407.nsukaddition.mixin.client.simukraft;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.xy2407.nsukaddition.client.autorestock.ClientAutoRestockCache;
import com.xy2407.nsukaddition.common.network.AutoRestockStatePacket;
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingMenuHolder;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingUiLayout;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingUiMetrics;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 在钻井控制盒左上角"钻井组件"框内加入自动补货/入库开关按钮。 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = MineralDrillingUiLayout.class, remap = false)
public abstract class MineralDrillingAutoRestockMixin {

    @Inject(method = "createRoot", at = @At("RETURN"), remap = false)
    private static void nsuk$addAutoRestockButton(MineralDrillingMenuHolder holder,
                                                  Player player,
                                                  MineralDrillingUiMetrics metrics,
                                                  MineralDrillingUiLayout.ClientActions clientActions,
                                                  MineralDrillingUiLayout.ProductTextResolver productTextResolver,
                                                  CallbackInfoReturnable<UIElement> cir) {
        UIElement root = cir.getReturnValue();
        if (root == null || holder == null || metrics == null) {
            return;
        }
        BlockPos boxPos = holder.boxPos();
        if (boxPos == null) {
            return;
        }
        List<UIElement> children = root.getChildren();
        if (children.isEmpty()) {
            return;
        }
        UIElement workspace = children.get(0);
        if (workspace == null) {
            return;
        }

        // 打开界面时向服务端请求当前自动补货状态并同步到本地缓存。
        PacketDistributor.sendToServer(new AutoRestockStatePacket(boxPos, false));

        final boolean[] enabled = {ClientAutoRestockCache.isEnabled(boxPos)};
        int btnW = 56;

        // 开关按钮：位于"钻井组件"框右上角。
        Button toggle = new Button();
        toggle.setText(Component.translatable(
                enabled[0] ? "gui.xy2407_nsuk_addition.on" : "gui.xy2407_nsuk_addition.off"));
        toggle.setOnClick(event -> {
            boolean next = !enabled[0];
            enabled[0] = next;
            ClientAutoRestockCache.set(boxPos, next);
            PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, next));
            toggle.setText(Component.translatable(
                    next ? "gui.xy2407_nsuk_addition.on" : "gui.xy2407_nsuk_addition.off"));
        });
        toggle.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(metrics.panelPadding() + metrics.leftPanelWidth() - btnW - 4);
            layout.top(metrics.panelPadding() + 4);
            layout.width(btnW);
            layout.height(12);
        });

        // 按钮下方的字段说明：告诉玩家这个开关的作用。
        Label caption = new Label();
        caption.setText(Component.literal("自动补货/入库"));
        caption.setAllowHitTest(false);
        caption.setOverflowVisible(false);
        caption.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(metrics.panelPadding() + metrics.leftPanelWidth() - btnW - 4);
            layout.top(metrics.panelPadding() + 18);
            layout.width(btnW);
            layout.height(12);
        });

        workspace.addChild(toggle);
        workspace.addChild(caption);
    }
}