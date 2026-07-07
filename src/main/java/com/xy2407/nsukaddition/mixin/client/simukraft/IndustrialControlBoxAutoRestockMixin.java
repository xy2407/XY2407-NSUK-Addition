package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.industrial.IndustrialControlBoxScreenOpener;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** 在工业控制箱标题栏注入自动补货开关按钮，支持一键切换补货状态。 */
@Mixin(IndustrialControlBoxScreenOpener.class)
public abstract class IndustrialControlBoxAutoRestockMixin {

    @Unique
    private static final Class<?> LAYOUT_METRICS_CLASS;

    @Unique
    private static final ConcurrentHashMap.KeySetView<BlockPos, Boolean> BUTTON_STATES =
            ConcurrentHashMap.newKeySet();

    static {
        try {
            LAYOUT_METRICS_CLASS = Class.forName(
                    "client.cn.kafei.simukraft.client.industrial.IndustrialControlBoxScreenOpener$LayoutMetrics");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to bind LayoutMetrics class", e);
        }
    }

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

    @Inject(method = "titleBar", at = @At("RETURN"), remap = false)
    private static void nsuk$addAutoRestockButton(@Coerce Object metrics,
                                                  CallbackInfoReturnable<UIElement> cir) {
        UIElement bar = cir.getReturnValue();
        if (bar == null) return;

        BlockPos boxPos = getOpenedBoxPos();
        if (boxPos == null) return;

        int titleH = getIntMetric(metrics, "titleBarHeight", 20);
        int btnW = getIntMetric(metrics, "doneButtonWidth", 60);

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
            layout.right(btnW * 2 + 4);
            layout.top(0);
            layout.width(btnW);
            layout.height(titleH);
        });
        onBtn.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(btnW + 2);
            layout.top(0);
            layout.width(btnW);
            layout.height(titleH);
        });
        offBtn.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(0);
            layout.top(0);
            layout.width(btnW);
            layout.height(titleH);
        });

        bar.addChild(anchor);
        bar.addChild(onBtn);
        bar.addChild(offBtn);
    }

    @Unique
    private static BlockPos getOpenedBoxPos() {
        try {
            java.lang.reflect.Field field = IndustrialControlBoxScreenOpener.class.getDeclaredField("openedBoxPos");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof BlockPos pos ? pos : null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    @Unique
    private static int getIntMetric(Object metrics, String methodName, int fallback) {
        try {
            Method m = LAYOUT_METRICS_CLASS.getDeclaredMethod(methodName);
            return (int) m.invoke(metrics);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return fallback;
        }
    }
}
