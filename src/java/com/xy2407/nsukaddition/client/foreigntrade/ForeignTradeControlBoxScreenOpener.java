package com.xy2407.nsukaddition.client.foreigntrade;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConstants;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenResponsePacket;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** 外贸控制箱客户端界面，白色内容区+黑色边框/字体的SimuKraft风格GUI。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ForeignTradeControlBoxScreenOpener {

    private static final int PANEL_BACKGROUND = 0xFFFFFFFF;
    private static final int BORDER_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF1A1A1A;
    private static final int TEXT_ACCENT_COLOR = 0xFF333333;
    private static final int TEXT_STATUS_COLOR = 0xFF555555;
    private static final int BUTTON_BASE = 0xFFF5F5F5;
    private static final int BUTTON_HOVER = 0xFFE8E8E8;
    private static final int BUTTON_PRESSED = 0xFFDCDCDC;
    private static final int BUTTON_BORDER = 0xFF1A1A1A;
    private static final int TITLE_BAR_BG = 0xFFE0E0E0;
    private static final int OPEN_MENU_BTN_BG = 0xFF4A90D9;
    private static final int OPEN_MENU_BTN_HOVER = 0xFF3A7BC8;
    private static final int OPEN_MENU_BTN_PRESSED = 0xFF2A6AB7;
    private static final float TEXT_ROLL_SPEED = 0.25F;

    private static final int MAX_PANEL_WIDTH = 400;
    private static final int MAX_PANEL_HEIGHT = 260;
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MIN_PANEL_HEIGHT = 180;

    private static BlockPos openedBoxPos;

    private ForeignTradeControlBoxScreenOpener() {}

    public static BlockPos getOpenedBoxPos() { return openedBoxPos; }

    public static void open(ForeignTradeControlBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        openedBoxPos = packet.boxPos().immutable();
        mc.execute(() -> mc.setScreen(new ForeignTradeControlBoxScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(ForeignTradeControlBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        LayoutMetrics m = layoutMetrics(screenWidth, screenHeight);

        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(m.rootPadding());
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(m.panelWidth());
            layout.height(m.panelHeight());
            layout.paddingAll(m.panelPadding());
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(m.gap());
        }).style(style -> style.backgroundTexture(
                new GuiTextureGroup(new ColorRectTexture(PANEL_BACKGROUND), new ColorBorderTexture(-1, BORDER_COLOR))
        ));

        panel.addChild(titleBar(packet, m));
        panel.addChild(infoSection(packet, m));
        panel.addChild(openMenuButton(m));
        panel.addChild(bottomRow(packet, m));

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(ForeignTradeControlBoxOpenResponsePacket packet, LayoutMetrics m) {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(m.titleBarHeight());
        }).style(style -> style.backgroundTexture(new ColorRectTexture(TITLE_BAR_BG)));

        bar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.title"),
                Horizontal.CENTER, TEXT_COLOR, m.titleBarHeight(), TextWrap.HIDE));
        bar.addChild(panelTopButton("gui.button.done", m.doneButtonWidth(), m.titleBarHeight(),
                ForeignTradeControlBoxScreenOpener::close));
        return bar;
    }

    private static UIElement infoSection(ForeignTradeControlBoxOpenResponsePacket packet, LayoutMetrics m) {
        UIElement section = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(m.innerGap());
            layout.paddingTop(m.gap());
        });

        section.addChild(label(workerLine(packet), Horizontal.LEFT, TEXT_ACCENT_COLOR, m.infoLineHeight(), TextWrap.HOVER_ROLL));
        section.addChild(label(statusLine(packet), Horizontal.LEFT, TEXT_STATUS_COLOR, m.infoLineHeight(), TextWrap.HOVER_ROLL));
        return section;
    }

    private static UIElement openMenuButton(LayoutMetrics m) {
        Button btn = new Button();
        btn.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.open_menu"));
        btn.setOnClick(event -> ForeignTradeMenuScreenOpener.open(openedBoxPos));
        btn.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED)
                .textColor(0xFFFFFFFF).textShadow(false));
        btn.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorRectTexture(OPEN_MENU_BTN_BG), new ColorBorderTexture(-1, BORDER_COLOR)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(OPEN_MENU_BTN_HOVER), new ColorBorderTexture(-1, BORDER_COLOR)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(OPEN_MENU_BTN_PRESSED), new ColorBorderTexture(-1, BORDER_COLOR)))
        );
        btn.layout(layout -> {
            layout.widthPercent(100);
            layout.height(m.actionHeight() + 4);
        });
        return btn;
    }

    private static UIElement bottomRow(ForeignTradeControlBoxOpenResponsePacket packet, LayoutMetrics m) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(m.actionHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(m.gap());
        });

        row.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.hire"),
                () -> hire(packet), !packet.hasWorker(), m.actionWidth(), m.actionHeight()));
        row.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.fire"),
                () -> action(packet, ForeignTradeControlBoxActionPacket.Action.FIRE),
                packet.hasWorker(), m.actionWidth(), m.actionHeight()));
        row.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.demolish"),
                () -> demolish(packet), true, m.actionWidth(), m.actionHeight()));

        return row;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(false)
                .textWrap(wrap)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Button panelTopButton(String key, int width, int height, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(width);
            layout.height(height);
        });
        return button;
    }

    private static Button flatButton(Component text, Runnable action, boolean active, int width, int height) {
        Button button = new Button();
        button.setText(text);
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED).textColor(TEXT_COLOR).textShadow(false));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.setActive(active);
        button.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorRectTexture(BUTTON_BASE), new ColorBorderTexture(-1, BUTTON_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BUTTON_HOVER), new ColorBorderTexture(-1, BUTTON_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BUTTON_PRESSED), new ColorBorderTexture(-1, BUTTON_BORDER)))
        );
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        return button;
    }

    private static Component workerLine(ForeignTradeControlBoxOpenResponsePacket packet) {
        Component value = packet.hasWorker()
                ? Component.literal(packet.workerName())
                : Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.none");
        return Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.worker_line", value);
    }

    private static Component statusLine(ForeignTradeControlBoxOpenResponsePacket packet) {
        Component status = Component.translatable(packet.statusKey());
        if (!packet.statusText().isBlank()) {
            status = status.copy().append(Component.literal(" " + packet.statusText()));
        }
        return Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.status_line", status);
    }

    private static void action(ForeignTradeControlBoxOpenResponsePacket packet, ForeignTradeControlBoxActionPacket.Action action) {
        PacketDistributor.sendToServer(new ForeignTradeControlBoxActionPacket(packet.boxPos(), action));
    }

    private static void hire(ForeignTradeControlBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), ForeignTradeConstants.HIRE_SOURCE_TYPE, ForeignTradeConstants.HIRE_ROLE);
    }

    private static void demolish(ForeignTradeControlBoxOpenResponsePacket packet) {
        openedBoxPos = null;
        Minecraft.getInstance().setScreen(null);
        PacketDistributor.sendToServer(new ForeignTradeControlBoxDemolishPacket(packet.boxPos()));
    }

    private static void close() {
        openedBoxPos = null;
        Minecraft.getInstance().setScreen(null);
    }

    private static LayoutMetrics layoutMetrics(int screenWidth, int screenHeight) {
        int rootPadding = clamp(Math.round(Math.min(screenWidth, screenHeight) * 0.018F), 4, 10);
        int availableWidth = Math.max(MIN_PANEL_WIDTH, screenWidth - rootPadding * 2);
        int availableHeight = Math.max(MIN_PANEL_HEIGHT, screenHeight - rootPadding * 2 - 24);
        int panelWidth = clamp(Math.min(MAX_PANEL_WIDTH, availableWidth), Math.min(MIN_PANEL_WIDTH, availableWidth), availableWidth);
        int panelHeight = clamp(Math.min(MAX_PANEL_HEIGHT, availableHeight), Math.min(MIN_PANEL_HEIGHT, availableHeight), availableHeight);
        int panelPadding = clamp(Math.round(panelWidth * 0.024F), 6, 12);
        int gap = clamp(Math.round(panelHeight * 0.018F), 3, 6);
        int innerGap = clamp(gap - 1, 2, 5);
        int titleBarHeight = clamp(Math.round(panelHeight * 0.085F), 18, 24);
        int infoLineHeight = clamp(Math.round(panelHeight * 0.060F), 12, 18);
        int actionHeight = clamp(Math.round(panelHeight * 0.085F), 22, 28);
        int actionWidth = clamp((panelWidth - panelPadding * 2 - gap * 2) / 3, 68, 108);
        int doneButtonWidth = clamp(Math.round(panelWidth * 0.16F), 50, 76);
        return new LayoutMetrics(rootPadding, panelWidth, panelHeight, panelPadding, gap, innerGap,
                titleBarHeight, infoLineHeight, actionHeight, actionWidth, doneButtonWidth);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record LayoutMetrics(int rootPadding, int panelWidth, int panelHeight, int panelPadding,
                                  int gap, int innerGap, int titleBarHeight, int infoLineHeight,
                                  int actionHeight, int actionWidth, int doneButtonWidth) {}

    private static final class ForeignTradeControlBoxScreen extends ModularUIScreen {
        private ForeignTradeControlBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void removed() {
            super.removed();
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof ForeignTradeControlBoxScreen)) {
                openedBoxPos = null;
            }
        }
    }
}
