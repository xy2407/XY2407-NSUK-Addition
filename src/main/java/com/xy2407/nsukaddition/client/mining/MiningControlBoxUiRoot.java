package com.xy2407.nsukaddition.client.mining;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
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
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
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

    private static final java.util.concurrent.ConcurrentHashMap.KeySetView<BlockPos, Boolean> RESTOCK_STATES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean isRestockOn(BlockPos pos) {
        return RESTOCK_STATES.contains(pos.immutable());
    }

    private static void setRestockState(BlockPos pos, boolean on) {
        BlockPos key = pos.immutable();
        if (on) {
            RESTOCK_STATES.add(key);
        } else {
            RESTOCK_STATES.remove(key);
        }
    }

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
            l.flexDirection(FlexDirection.COLUMN);
            l.alignItems(AlignItems.STRETCH);
            l.gapAll(metrics.gap);
        }).addClass("simukraft_panel");

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
        int rootPadding = clamp(Math.round(Math.min(screenWidth, screenHeight) * 0.018F), 4, 10);
        int availableWidth = Math.max(MIN_PANEL_WIDTH, screenWidth - rootPadding * 2);
        int availableHeight = Math.max(MIN_PANEL_HEIGHT, screenHeight - rootPadding * 2 - 24);
        int panelWidth = clamp(Math.min(MAX_PANEL_WIDTH, availableWidth), Math.min(MIN_PANEL_WIDTH, availableWidth), availableWidth);
        int panelHeight = clamp(Math.min(MAX_PANEL_HEIGHT, availableHeight), Math.min(MIN_PANEL_HEIGHT, availableHeight), availableHeight);
        int panelPadding = clamp(Math.round(panelWidth * 0.024F), 6, 12);
        int gap = clamp(Math.round(panelHeight * 0.018F), 3, 6);
        int innerGap = clamp(gap - 1, 2, 5);
        int titleHeight = clamp(Math.round(panelHeight * 0.080F), 14, 20);
        int doneButtonHeight = clamp(Math.round(panelHeight * 0.078F), 18, 24);
        int titleBarHeight = Math.max(titleHeight, doneButtonHeight);
        int doneButtonWidth = clamp(Math.round(panelWidth * 0.16F), 50, 76);
        int toolButtonHeight = clamp(Math.round(panelHeight * 0.085F), 18, 24);
        int toolWidth = clamp(Math.round(panelWidth * 0.235F), 86, 112);
        int actionHeight = clamp(Math.round(panelHeight * 0.078F), 20, 24);
        int actionWidth = clamp((panelWidth - panelPadding * 2 - gap * 2) / 3, 84, 132);
        return new LayoutMetrics(rootPadding, panelPadding, panelWidth, panelHeight, gap, innerGap,
                titleBarHeight, doneButtonWidth, doneButtonHeight, toolWidth, toolButtonHeight, actionWidth, actionHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record LayoutMetrics(
            int rootPadding, int panelPadding, int panelWidth, int panelHeight,
            int gap, int innerGap, int titleBarHeight, int doneButtonWidth,
            int doneButtonHeight, int toolWidth, int toolButtonHeight,
            int actionWidth, int actionHeight) {}

    private UIElement titleBar(LayoutMetrics m) {
        int titleH = m.titleBarHeight;
        int doneW = m.doneButtonWidth;
        int btnH = Math.max(12, titleH - 8);
        int lblH = titleH - btnH;
        int btnW = doneW;
        UIElement bar = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(titleH);
        });
        bar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.mining.title"),
                Horizontal.CENTER, 0xFFFFFFFF, titleH, TextWrap.HIDE));

        Button doneBtn = new Button();
        doneBtn.setText(Component.translatable("gui.button.done"));
        doneBtn.setOnClick(e -> close());
        doneBtn.layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(0);
            l.top(0);
            l.width(doneW);
            l.height(titleH);
        });
        bar.addChild(doneBtn);

        BlockPos boxPos = packet.boxPos();
        if (boxPos != null) {
            boolean on = isRestockOn(boxPos);

            Label anchor = new Label();
            anchor.setText(Component.translatable("gui.xy2407_nsuk_addition.autorestock.toggle"));
            anchor.setOverflowVisible(false);
            anchor.textStyle(style -> style.textColor(0xFFFFFFFF).textShadow(true)
                    .textWrap(TextWrap.HIDE)
                    .textAlignHorizontal(Horizontal.CENTER)
                    .textAlignVertical(Vertical.CENTER));

            Button onBtn = new Button();
            onBtn.setText(Component.literal("开"));
            onBtn.setActive(!on);

            Button offBtn = new Button();
            offBtn.setText(Component.literal("关"));
            offBtn.setActive(on);

            onBtn.setOnClick(event -> {
                setRestockState(boxPos, true);
                PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, true));
                onBtn.setActive(false);
                offBtn.setActive(true);
            });
            offBtn.setOnClick(event -> {
                setRestockState(boxPos, false);
                PacketDistributor.sendToServer(new AutoRestockTogglePacket(boxPos, false));
                onBtn.setActive(true);
                offBtn.setActive(false);
            });

            anchor.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.right(0);
                layout.top(0);
                layout.width(btnW * 2 + 2);
                layout.height(lblH);
            });
            onBtn.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.right(btnW + 2);
                layout.top(lblH);
                layout.width(btnW);
                layout.height(btnH);
            });
            offBtn.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.right(0);
                layout.top(lblH);
                layout.width(btnW);
                layout.height(btnH);
            });

            bar.addChild(anchor);
            bar.addChild(onBtn);
            bar.addChild(offBtn);
        }

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
            l.width(m.toolWidth);
            l.heightPercent(100);
            l.flexDirection(FlexDirection.COLUMN);
            l.alignItems(AlignItems.STRETCH);
            l.justifyContent(AlignContent.CENTER);
            l.gapAll(m.innerGap);
            l.flexShrink(0);
        });
        tools.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.demolish"),
                this::demolish, true, m.toolWidth, m.toolButtonHeight));
        tools.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.bind_container"),
                this::bindContainer, true, m.toolWidth, m.toolButtonHeight));
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
        }).style(s -> s.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0xFF333333)));
        bar.addChild(bg);

        progressFg = new UIElement().layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(0);
            l.top(2);
            l.width(0);
            l.height(barH);
        }).style(s -> s.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0xFF55AA55)));
        bar.addChild(progressFg);

        progressText = label(Component.empty(), Horizontal.CENTER, 0xFFFFFFFF, barH + 4, TextWrap.HIDE);
        bar.addChild(progressText);
        return bar;
    }

    private UIElement actionRow(LayoutMetrics m) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(m.actionHeight);
            l.flexDirection(FlexDirection.ROW);
            l.flexWrap(FlexWrap.WRAP);
            l.justifyContent(AlignContent.CENTER);
            l.gapAll(m.gap);
        });

        startStopButton = flatButton(Component.empty(), () -> action(MiningControlBoxActionPacket.Action.TOGGLE_RUN), false, m.actionWidth, m.actionHeight);
        hireButton = flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.hire"), this::hire, false, m.actionWidth, m.actionHeight);
        fireButton = flatButton(Component.translatable("gui.xy2407_nsuk_addition.mining.fire"), () -> action(MiningControlBoxActionPacket.Action.FIRE), false, m.actionWidth, m.actionHeight);
        boundsButton = flatButton(Component.empty(), this::toggleBounds, false, m.actionWidth, m.actionHeight);

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
        startStopButton.setText(toggleText());
        startStopButton.setActive(packet.hasWorker() && hasPickaxe);
        hireButton.setActive(!packet.hasWorker());
        fireButton.setActive(packet.hasWorker());
        boundsButton.setText(boundsText());
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
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[NSUK] 雇佣按钮点击 → sourceType=" + MiningConstants.HIRE_SOURCE_TYPE + " role=" + MiningConstants.HIRE_ROLE + " pos=" + packet.boxPos()), false);
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
        Button btn = new Button();
        btn.setText(text);
        btn.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
        btn.setOnClick(e -> action.run());
        btn.setActive(active);
        btn.layout(l -> {
            l.width(width);
            l.height(height);
            l.flexShrink(0);
        });
        return btn;
    }
}
