package com.xy2407.nsukaddition.client.mining;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.xy2407.nsukaddition.common.block.entity.MiningControlBoxBlockEntity;
import com.xy2407.nsukaddition.common.mining.MiningConstants;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxViewUpdatePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** 采矿控制箱的客户端 UI 根节点，构建界面布局并处理交互与动态刷新。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class MiningControlBoxUiRoot extends UIElement {

    private static final int MAX_PANEL_WIDTH = 380;
    private static final int MAX_PANEL_HEIGHT = 280;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MIN_PANEL_HEIGHT = 200;
    private static final float TEXT_ROLL_SPEED = 0.25F;
    private static final int SLOT_SIZE = 18;

    private static final int BTN_BASE = 0xFFE0E0E0;
    private static final int BTN_HOVER = 0xFFF0F0F0;
    private static final int BTN_BORDER = 0xFF1E1E1E;

    private static WeakReference<MiningControlBoxUiRoot> activeRoot = new WeakReference<>(null);

    private MiningControlBoxOpenResponsePacket packet;

    private Label workerLabel;
    private Label yLevelLabel;
    private Label statusLabel;
    private UIElement progressFg;
    private Label progressText;
    private Button startStopButton;
    private Button hireButton;
    private Button fireButton;
    private Button boundsButton;
    private Label startStopLabel;
    private Label boundsLabel;

    public MiningControlBoxUiRoot(MiningControlBoxOpenResponsePacket packet) {
        this.packet = packet;
        activeRoot = new WeakReference<>(this);
        buildUi();
    }

    private void buildUi() {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = Math.max(320, mc.getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, mc.getWindow().getGuiScaledHeight());
        LayoutMetrics metrics = layoutMetrics(screenWidth, screenHeight);

        layout(l -> {
            l.widthPercent(100);
            l.heightPercent(100);
            l.alignItems(AlignItems.CENTER);
            l.justifyContent(AlignContent.CENTER);
            l.paddingAll(metrics.rootPadding);
        });

        addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));

        UIElement panel = new UIElement().layout(l -> {
            l.width(metrics.panelWidth);
            l.height(metrics.panelHeight);
            l.paddingAll(metrics.panelPadding);
            l.marginTop(-22);
            l.flexDirection(FlexDirection.COLUMN);
            l.alignItems(AlignItems.STRETCH);
            l.gapAll(metrics.gap);
        }).style(s -> s.backgroundTexture(new ColorRectTexture(0xFF444444))).addClass("simukraft_panel");

        panel.addChild(titleBar(metrics));
        panel.addChild(pickaxeSlots(metrics));
        panel.addChild(header(metrics));
        panel.addChild(progressBar(metrics));
        panel.addChild(actionRow(metrics));
        panel.addChild(playerInventorySlots(metrics));

        addChild(panel);
        refreshDynamicElements();
    }

    private static LayoutMetrics layoutMetrics(int screenWidth, int screenHeight) {
        int rootPadding = Math.max(4, Math.round(Math.min(screenWidth, screenHeight) * 0.018F));
        int panelPadding = Math.max(6, Math.round(Math.min(screenWidth, screenHeight) * 0.014F));
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, screenWidth - rootPadding * 2));
        int panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, screenHeight - rootPadding * 2));
        int gap = Math.max(3, Math.round(panelHeight * 0.016F));
        int innerGap = Math.max(2, Math.round(panelHeight * 0.01F));
        return new LayoutMetrics(rootPadding, panelPadding, panelWidth, panelHeight, gap, innerGap);
    }

    private record LayoutMetrics(
            int rootPadding, int panelPadding, int panelWidth, int panelHeight,
            int gap, int innerGap) {}

    private UIElement titleBar(LayoutMetrics m) {
        UIElement bar = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(18);
        });
        bar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.mining.title"),
                Horizontal.CENTER, 0xFFFFFFFF, 18, TextWrap.HIDE));
        Button doneBtn = flatButton(Component.translatable("gui.button.done"), this::close, true, 50, 18);
        doneBtn.layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.right(0);
            l.top(0);
        });
        bar.addChild(doneBtn);
        return bar;
    }

    private UIElement pickaxeSlots(LayoutMetrics m) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(SLOT_SIZE + 4);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(2);
            l.justifyContent(AlignContent.CENTER);
            l.alignItems(AlignItems.CENTER);
        });

        BlockEntity be = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getBlockEntity(packet.boxPos())
                : null;
        net.neoforged.neoforge.items.ItemStackHandler handler = be instanceof MiningControlBoxBlockEntity box
                ? box.pickaxes()
                : null;

        for (int i = 0; i < MiningControlBoxBlockEntity.PICKAXE_SLOTS; i++) {
            ItemSlot slot = new ItemSlot();
            if (handler != null) {
                slot.bind(handler, i);
            }
            slot.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE).flexShrink(0));
            slot.slotStyle(s -> s.acceptQuickMove(true));
            row.addChild(slot);
        }
        return row;
    }

    private UIElement header(LayoutMetrics m) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(70);
            l.flexDirection(FlexDirection.ROW);
            l.alignItems(AlignItems.STRETCH);
            l.gapAll(m.gap);
        });

        UIElement info = new UIElement().layout(l -> {
            l.flex(1);
            l.heightPercent(100);
            l.flexDirection(FlexDirection.COLUMN);
            l.alignItems(AlignItems.STRETCH);
            l.gapAll(m.innerGap);
            l.flexShrink(1);
        });
        info.setOverflowVisible(false);

        int lineH = 16;
        int infoColor = 0xFFF5F5A0;
        workerLabel = label(Component.empty(), Horizontal.LEFT, infoColor, lineH, TextWrap.HOVER_ROLL);
        yLevelLabel = label(Component.empty(), Horizontal.LEFT, infoColor, lineH, TextWrap.HIDE);
        statusLabel = label(Component.empty(), Horizontal.LEFT, 0xFFE0E0FF, lineH, TextWrap.HOVER_ROLL);
        info.addChild(workerLabel);
        info.addChild(yLevelLabel);
        info.addChild(statusLabel);
        row.addChild(info);

        UIElement tools = new UIElement().layout(l -> {
            l.width(80);
            l.heightPercent(100);
            l.flexDirection(FlexDirection.COLUMN);
            l.alignItems(AlignItems.STRETCH);
            l.justifyContent(AlignContent.CENTER);
            l.gapAll(m.innerGap);
            l.flexShrink(0);
        });
        tools.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.demolish"),
                this::demolish, true, 80, 22));
        tools.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.bind_container"),
                this::bindContainer, true, 80, 22));
        row.addChild(tools);

        return row;
    }

    private UIElement progressBar(LayoutMetrics m) {
        int barW = m.panelWidth - m.panelPadding * 2;
        int barH = 14;

        UIElement bar = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(barH + 4);
            l.paddingTop(2);
        });

        UIElement bg = new UIElement().layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(0);
            l.top(2);
            l.width(barW);
            l.height(barH);
        }).style(s -> s.backgroundTexture(new ColorRectTexture(0xFF333333)));
        bar.addChild(bg);

        progressFg = new UIElement().layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(0);
            l.top(2);
            l.width(0);
            l.height(barH);
        }).style(s -> s.backgroundTexture(new ColorRectTexture(0xFF55AA55)));
        bar.addChild(progressFg);

        progressText = label(Component.empty(), Horizontal.CENTER, 0xFFFFFFFF, barH + 4, TextWrap.HIDE);
        bar.addChild(progressText);
        return bar;
    }

    private UIElement actionRow(LayoutMetrics m) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(22);
            l.flexDirection(FlexDirection.ROW);
            l.flexWrap(FlexWrap.WRAP);
            l.justifyContent(AlignContent.CENTER);
            l.gapAll(m.gap);
        });
        int btnW = 100;
        int btnH = 22;

        var startStop = flatButtonWithLabel(Component.empty(), () -> action(MiningControlBoxActionPacket.Action.TOGGLE_RUN), true, btnW, btnH);
        startStopButton = startStop.btn();
        startStopLabel = startStop.lbl();
        hireButton = flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.hire"), this::hire, true, btnW, btnH);
        fireButton = flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.fire"), () -> action(MiningControlBoxActionPacket.Action.FIRE), true, btnW, btnH);
        var bounds = flatButtonWithLabel(Component.empty(), this::toggleBounds, true, btnW, btnH);
        boundsButton = bounds.btn();
        boundsLabel = bounds.lbl();

        row.addChild(startStopButton);
        row.addChild(hireButton);
        row.addChild(fireButton);
        row.addChild(boundsButton);
        return row;
    }

    private InventorySlots playerInventorySlots(LayoutMetrics m) {
        InventorySlots slots = new InventorySlots();
        slots.layout(l -> l.widthPercent(100).height(76));
        return slots;
    }

    public static void refreshActive(MiningControlBoxOpenResponsePacket packet) {
        MiningControlBoxUiRoot root = activeRoot.get();
        if (root != null && root.isSameSession(packet)) {
            root.updatePacket(packet);
        }
    }

    public static void refreshActive(MiningControlBoxViewUpdatePacket packet) {
        MiningControlBoxUiRoot root = activeRoot.get();
        if (root != null && root.packet.boxPos().equals(packet.boxPos())) {
            root.updatePacket(packet);
        }
    }

    private boolean isSameSession(MiningControlBoxOpenResponsePacket next) {
        return next != null && Objects.equals(packet.boxPos(), next.boxPos());
    }

    private void updatePacket(MiningControlBoxOpenResponsePacket next) {
        this.packet = next;
        refreshDynamicElements();
    }

    private void updatePacket(MiningControlBoxViewUpdatePacket p) {
        this.packet = new MiningControlBoxOpenResponsePacket(
                p.boxPos(), p.hasWorker(), p.workerName(), p.currentYLevel(),
                p.running(), p.workTicks(), p.maxWorkTicks(), p.statusKey(), p.statusText());
        refreshDynamicElements();
    }

    private void refreshDynamicElements() {
        workerLabel.setText(workerLine());
        yLevelLabel.setText(Component.literal("Y=" + packet.currentYLevel()));
        statusLabel.setText(statusLine());

        int maxTicks = packet.maxWorkTicks() > 0 ? packet.maxWorkTicks() : MiningConstants.TICKS_PER_LAYER;
        float pct = maxTicks > 0 ? (float) packet.workTicks() / (float) maxTicks : 0f;
        Minecraft mc = Minecraft.getInstance();
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH,
                mc.getWindow().getGuiScaledWidth() - Math.max(4, Math.round(Math.min(
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()) * 0.018F)) * 2));
        int barW = panelWidth - 20;
        int filled = Math.round(barW * pct);
        progressFg.layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(0);
            l.top(2);
            l.width(filled);
            l.height(14);
        });
        progressText.setText(Component.literal(packet.workTicks() + " / " + maxTicks + " tick"));

        boolean hasPickaxe = hasPickaxeAt(packet.boxPos());
        startStopLabel.setText(toggleText());
        startStopButton.setActive(packet.hasWorker() && hasPickaxe);
        hireButton.setActive(!packet.hasWorker());
        fireButton.setActive(packet.hasWorker());
        boundsLabel.setText(boundsText());
    }

    private static boolean hasPickaxeAt(BlockPos boxPos) {
        if (Minecraft.getInstance().level == null) return false;
        BlockEntity be = Minecraft.getInstance().level.getBlockEntity(boxPos);
        return be instanceof MiningControlBoxBlockEntity box && box.hasPickaxe();
    }

    private Component workerLine() {
        Component value = packet.hasWorker() ? Component.literal(packet.workerName())
                : Component.translatable("gui.xy2407_nsuk_addition.mining.none");
        return Component.translatable("gui.xy2407_nsuk_addition.mining.worker", value);
    }

    private Component statusLine() {
        Component status = Component.translatable(packet.statusKey());
        if (!packet.statusText().isBlank()) {
            status = status.copy().append(Component.literal(" " + packet.statusText()));
        }
        return Component.translatable("gui.xy2407_nsuk_addition.mining.status", status);
    }

    private Component toggleText() {
        return Component.translatable(packet.running()
                ? "gui.xy2407_nsuk_addition.mining.stop" : "gui.xy2407_nsuk_addition.mining.start");
    }

    private Component boundsText() {
        boolean visible = BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos());
        return Component.translatable(visible
                ? "gui.xy2407_nsuk_addition.mining.hide_bounds" : "gui.xy2407_nsuk_addition.mining.show_bounds");
    }

    private void toggleBounds() {
        BlockPos boxPos = packet.boxPos().immutable();
        if (BuildingBoundsRenderer.isBuildingBoundsVisible(boxPos)) {
            BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, null, false);
        } else {
            BlockPos mineStart = boxPos.south(1);
            int y = packet.currentYLevel();
            AABB bounds = new AABB(
                    mineStart.getX(), y, mineStart.getZ(),
                    mineStart.getX() + MiningConstants.MINE_AREA_SIZE, y + 1, mineStart.getZ() + MiningConstants.MINE_AREA_SIZE
            );
            BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, bounds, true);
        }
        boundsButton.setText(boundsText());
    }

    private void action(MiningControlBoxActionPacket.Action act) {
        PacketDistributor.sendToServer(new MiningControlBoxActionPacket(packet.boxPos(), act));
    }

    private void hire() {
        NpcHireScreen.request(packet.boxPos(), MiningConstants.HIRE_SOURCE_TYPE, MiningConstants.HIRE_ROLE);
    }

    private void demolish() {
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        Minecraft.getInstance().setScreen(null);
        PacketDistributor.sendToServer(new MiningControlBoxDemolishPacket(packet.boxPos()));
    }

    private void bindContainer() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("\u00a7a\u5bb9\u5668\u81ea\u52a8\u68c0\u6d4b\uff1a\u91c7\u77ff\u63a7\u5236\u7bb1\u5c06\u81ea\u52a8\u4f7f\u7528\u9644\u8fd15\u683c\u5185\u7684\u5bb9\u5668"), false);
        }
    }

    private void close() {
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        Minecraft.getInstance().setScreen(null);
    }

    private static Label label(Component text, Horizontal h, int color, int height, TextWrap wrap) {
        Label lbl = new Label();
        lbl.setText(text);
        lbl.setOverflowVisible(false);
        lbl.layout(l -> {
            l.widthPercent(100);
            l.height(height);
        });
        lbl.textStyle(s -> s.textColor(color).textShadow(true)
                .textWrap(wrap).textAlignHorizontal(h).textAlignVertical(Vertical.CENTER));
        return lbl;
    }

    private static Button flatButton(Component text, Runnable action, boolean active, int width, int height) {
        Button btn = new Button().noText();
        btn.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorBorderTexture(1, BTN_BORDER), new ColorRectTexture(BTN_BASE)))
                .hoverTexture(new GuiTextureGroup(new ColorBorderTexture(1, BTN_BORDER), new ColorRectTexture(BTN_HOVER)))
                .pressedTexture(new GuiTextureGroup(new ColorBorderTexture(1, BTN_BORDER), new ColorRectTexture(BTN_HOVER))));
        btn.setOnClick(e -> {
            if (btn.isActive()) action.run();
        });
        btn.setActive(active);
        btn.layout(l -> {
            l.width(width);
            l.height(height);
            l.flexShrink(0);
        });

        Label lbl = new Label();
        lbl.setText(text);
        lbl.setOverflowVisible(false);
        lbl.layout(l -> {
            l.widthPercent(100);
            l.heightPercent(100);
        });
        lbl.textStyle(s -> s.textColor(active ? 0xFF222222 : 0xFF888888).textShadow(false)
                .textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        btn.addChild(lbl);
        return btn;
    }

    private static Button topButton(String key, int left, int top, int width, int height, Runnable action) {
        Button btn = new Button();
        btn.setText(Component.translatable(key));
        btn.setOnClick(e -> action.run());
        btn.layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(left);
            l.top(top);
            l.width(width);
            l.height(height);
        });
        return btn;
    }

    private static ButtonWithLabel flatButtonWithLabel(Component text, Runnable action, boolean active, int width, int height) {
        Button btn = new Button().noText();
        btn.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorBorderTexture(1, BTN_BORDER), new ColorRectTexture(BTN_BASE)))
                .hoverTexture(new GuiTextureGroup(new ColorBorderTexture(1, BTN_BORDER), new ColorRectTexture(BTN_HOVER)))
                .pressedTexture(new GuiTextureGroup(new ColorBorderTexture(1, BTN_BORDER), new ColorRectTexture(BTN_HOVER))));
        btn.setOnClick(e -> {
            if (btn.isActive()) action.run();
        });
        btn.setActive(active);
        btn.layout(l -> {
            l.width(width);
            l.height(height);
            l.flexShrink(0);
        });
        Label lbl = new Label();
        lbl.setText(text);
        lbl.setOverflowVisible(false);
        lbl.layout(l -> {
            l.widthPercent(100);
            l.heightPercent(100);
        });
        lbl.textStyle(s -> s.textColor(active ? 0xFF222222 : 0xFF888888).textShadow(false)
                .textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        btn.addChild(lbl);
        return new ButtonWithLabel(btn, lbl);
    }

    private record ButtonWithLabel(Button btn, Label lbl) {}
}
