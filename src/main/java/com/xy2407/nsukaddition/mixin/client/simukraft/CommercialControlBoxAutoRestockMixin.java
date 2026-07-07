package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.commercial.CommercialControlBoxScreenOpener;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
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

import java.util.concurrent.ConcurrentHashMap;

/** 在商业控制箱界面标题栏注入自动补货开关按钮，支持一键切换补货状态。 */
@Mixin(CommercialControlBoxScreenOpener.class)
public abstract class CommercialControlBoxAutoRestockMixin {

    @Unique
    private static final ConcurrentHashMap.KeySetView<BlockPos, Boolean> BUTTON_STATES =
            ConcurrentHashMap.newKeySet();

    @Unique
    private static boolean isOn(BlockPos pos) {
        return BUTTON_STATES.contains(pos.immutable());
    }

    @Unique
    private static void setState(BlockPos pos, boolean on) {
        BlockPos key = pos.immutable();
        if (on) {
            BUTTON_STATES.add(key);
        } else {
            BUTTON_STATES.remove(key);
        }
    }

    @Inject(method = "createUi", at = @At("RETURN"), remap = false)
    private static void nsuk$addRestockButtons(CommercialControlBoxOpenResponsePacket packet,
                                               CallbackInfoReturnable<ModularUI> cir) {
        BlockPos boxPos = packet.boxPos();
        if (boxPos == null) return;

        ModularUI modularUI = cir.getReturnValue();
        if (modularUI == null) return;

        UIElement root = getRoot(modularUI);
        if (root == null) return;

        Label anchor = new Label();
        anchor.setText(Component.translatable("gui.xy2407_nsuk_addition.autorestock.toggle"));
        anchor.textStyle(style -> style.textColor(0xFFFFFFFF).textShadow(true));

        Button onBtn = new Button();
        onBtn.setText(Component.literal("开"));

        Button offBtn = new Button();
        offBtn.setText(Component.literal("关"));

        boolean on = isOn(boxPos);
        onBtn.setActive(!on);
        offBtn.setActive(on);

        onBtn.setOnClick(event -> {
            setState(boxPos, true);
            PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, true));
            onBtn.setActive(false);
            offBtn.setActive(true);
        });
        offBtn.setOnClick(event -> {
            setState(boxPos, false);
            PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, false));
            onBtn.setActive(true);
            offBtn.setActive(false);
        });

        anchor.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(116);
            layout.top(5);
            layout.width(36);
            layout.height(22);
        });
        onBtn.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(78);
            layout.top(5);
            layout.width(36);
            layout.height(22);
        });
        offBtn.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(40);
            layout.top(5);
            layout.width(36);
            layout.height(22);
        });

        root.addChild(anchor);
        root.addChild(onBtn);
        root.addChild(offBtn);
    }

    @Unique
    private static UIElement getRoot(ModularUI modularUI) {
        try {
            java.lang.reflect.Field field = ModularUI.class.getDeclaredField("root");
            field.setAccessible(true);
            return (UIElement) field.get(modularUI);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }
}
