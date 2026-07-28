package com.xy2407.nsukaddition.client.cooking;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMenuSelectPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 餐厅控制箱客户端界面，白色内容区+黑色边框/字体的SimuKraft风格GUI。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RestaurantControlBoxScreenOpener {

    private static final int PANEL_BG = 0xFFFFFFFF;
    private static final int BORDER = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF1A1A1A;
    private static final int TEXT_ACCENT = 0xFF333333;
    private static final int TEXT_STATUS = 0xFF555555;
    private static final int BTN_BASE = 0xFFF5F5F5;
    private static final int BTN_HOVER = 0xFFE8E8E8;
    private static final int BTN_PRESSED = 0xFFDCDCDC;
    private static final int BTN_BORDER = 0xFF1A1A1A;
    private static final int TITLE_BG = 0xFFE0E0E0;
    private static final float ROLL_SPEED = 0.25F;

    private RestaurantControlBoxScreenOpener() {}

    private static RestaurantControlBoxOpenResponsePacket currentPacket;

    public static void open(RestaurantControlBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        currentPacket = packet;
        mc.execute(() -> mc.setScreen(new RestaurantScreen(createUi(packet), Component.empty())));
    }

    public static void refreshIfOpen(RestaurantControlBoxOpenResponsePacket packet) {
        if (currentPacket != null && packet.boxPos().equals(currentPacket.boxPos())) {
            currentPacket = packet;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.screen instanceof RestaurantScreen) {
                mc.execute(() -> mc.setScreen(new RestaurantScreen(createUi(packet), Component.empty())));
            }
        }
    }

    private static ModularUI createUi(RestaurantControlBoxOpenResponsePacket packet) {
        int sw = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int sh = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        int pp = clamp(Math.round(Math.min(sw, sh) * 0.018F), 4, 10);
        int pw = clamp(Math.min(360, sw - pp * 2), 260, sw - pp * 2);
        int ph = clamp(Math.min(250, sh - pp * 2 - 24), 180, sh - pp * 2);
        int ip = clamp(Math.round(pw * 0.024F), 6, 12);
        int gap = clamp(Math.round(ph * 0.018F), 3, 6);

        UIElement root = new UIElement().layout(l -> { l.widthPercent(100); l.heightPercent(100); l.alignItems(AlignItems.CENTER); l.justifyContent(AlignContent.CENTER); l.paddingAll(pp); });
        root.addChild(SimuKraftUiTheme.createShellPanel(sw, sh));

        UIElement panel = new UIElement().layout(l -> { l.width(pw); l.height(ph); l.paddingAll(ip); l.flexDirection(FlexDirection.COLUMN); l.alignItems(AlignItems.STRETCH); l.gapAll(gap); })
                .style(s -> s.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(PANEL_BG), new ColorBorderTexture(-1, BORDER))));

        int tbH = clamp(Math.round(ph * 0.085F), 18, 24);
        int ilH = clamp(Math.round(ph * 0.060F), 12, 18);
        int acH = clamp(Math.round(ph * 0.085F), 22, 28);
        int acW = clamp((pw - ip * 2 - gap * 2) / 3, 68, 108);
        int dbW = clamp(Math.round(pw * 0.16F), 50, 76);

        panel.addChild(titleBar(tbH, dbW));
        panel.addChild(infoSection(packet, ilH, gap));
        panel.addChild(menuSelectButton(acH));
        panel.addChild(toggleButton(acH));
        panel.addChild(autoRestockRow(packet, acH, gap));
        panel.addChild(bottomRow(packet, acH, acW, gap));

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(int height, int doneWidth) {
        UIElement bar = new UIElement().layout(l -> { l.widthPercent(100); l.height(height); }).style(s -> s.backgroundTexture(new ColorRectTexture(TITLE_BG)));
        bar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.title"), Horizontal.CENTER, TEXT_COLOR, height, TextWrap.HIDE));
        Button close = new Button(); close.setText(Component.translatable("gui.button.done"));
        close.setOnClick(e -> close());
        close.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(0); l.top(0); l.width(doneWidth); l.height(height); });
        bar.addChild(close);
        return bar;
    }

    private static UIElement infoSection(RestaurantControlBoxOpenResponsePacket p, int lineH, int gap) {
        UIElement sec = new UIElement().layout(l -> { l.widthPercent(100); l.flex(1); l.flexDirection(FlexDirection.COLUMN); l.alignItems(AlignItems.STRETCH); l.gapAll(gap); l.paddingTop(gap); });
        sec.addChild(label(workerLine(p), Horizontal.LEFT, TEXT_ACCENT, lineH, TextWrap.HOVER_ROLL));
        sec.addChild(label(statusLine(p), Horizontal.LEFT, TEXT_STATUS, lineH, TextWrap.HOVER_ROLL));
        return sec;
    }

    private static UIElement toggleButton(int height) {
        Button btn = new Button();
        btn.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.toggle"));
        btn.setOnClick(e -> action(RestaurantControlBoxActionPacket.Action.TOGGLE_RUN));
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED).textColor(TEXT_COLOR).textShadow(false));
        btn.buttonStyle(s -> s.baseTexture(new GuiTextureGroup(new ColorRectTexture(BTN_BASE), new ColorBorderTexture(-1, BTN_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BTN_HOVER), new ColorBorderTexture(-1, BTN_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BTN_PRESSED), new ColorBorderTexture(-1, BTN_BORDER))));
        btn.layout(l -> { l.widthPercent(100); l.height(height); });
        return btn;
    }

    private static UIElement menuSelectButton(int height) {
        Button btn = new Button();
        btn.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.menu_select"));
        btn.setOnClick(e -> openMenuSelect(currentPacket));
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED).textColor(TEXT_COLOR).textShadow(false));
        btn.buttonStyle(s -> s.baseTexture(new GuiTextureGroup(new ColorRectTexture(BTN_BASE), new ColorBorderTexture(-1, BTN_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BTN_HOVER), new ColorBorderTexture(-1, BTN_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BTN_PRESSED), new ColorBorderTexture(-1, BTN_BORDER))));
        btn.layout(l -> { l.widthPercent(100); l.height(height); });
        return btn;
    }

    private static UIElement autoRestockRow(RestaurantControlBoxOpenResponsePacket p, int height, int gap) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100); l.height(height);
            l.flexDirection(FlexDirection.ROW); l.alignItems(AlignItems.CENTER); l.gapAll(gap);
        });

        boolean enabled = p.autoRestock();
        Label statusLabel = new Label();
        statusLabel.setText(Component.translatable(enabled
                ? "gui.xy2407_nsuk_addition.cooking.auto_restock_on"
                : "gui.xy2407_nsuk_addition.cooking.auto_restock_off"));
        statusLabel.layout(l -> { l.flex(1); l.height(height); });
        statusLabel.textStyle(s -> s.textColor(enabled ? 0xFF2E7D32 : TEXT_STATUS).textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER).textWrap(TextWrap.HIDE));
        row.addChild(statusLabel);

        Button toggle = new Button();
        toggle.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.auto_restock_toggle"));
        toggle.setOnClick(e -> action(RestaurantControlBoxActionPacket.Action.TOGGLE_AUTORESTOCK));
        toggle.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED).textColor(TEXT_COLOR).textShadow(false));
        toggle.buttonStyle(s -> s.baseTexture(new GuiTextureGroup(new ColorRectTexture(enabled ? 0xFFC8E6C9 : BTN_BASE), new ColorBorderTexture(-1, BTN_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(enabled ? 0xFFB2DFDB : BTN_HOVER), new ColorBorderTexture(-1, BTN_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BTN_PRESSED), new ColorBorderTexture(-1, BTN_BORDER))));
        toggle.layout(l -> { l.width(80); l.height(height); l.flexShrink(0); });
        row.addChild(toggle);

        return row;
    }

    private static UIElement bottomRow(RestaurantControlBoxOpenResponsePacket p, int ah, int aw, int gap) {
        UIElement row = new UIElement().layout(l -> { l.widthPercent(100); l.height(ah); l.flexDirection(FlexDirection.ROW); l.flexWrap(FlexWrap.WRAP); l.justifyContent(AlignContent.CENTER); l.gapAll(gap); });
        row.addChild(toggleButton(
                Component.translatable("gui.xy2407_nsuk_addition.cooking.hire_chef"),
                Component.translatable("gui.xy2407_nsuk_addition.cooking.fire_chef"),
                p.hasWorker(), () -> hire(RestaurantConstants.HIRE_ROLE_CHEF),
                () -> action(RestaurantControlBoxActionPacket.Action.FIRE_CHEF), aw, ah));
        row.addChild(toggleButton(
                Component.translatable("gui.xy2407_nsuk_addition.cooking.hire_waiter"),
                Component.translatable("gui.xy2407_nsuk_addition.cooking.fire_waiter"),
                p.hasWaiter(), () -> hire(RestaurantConstants.HIRE_ROLE_WAITER),
                () -> action(RestaurantControlBoxActionPacket.Action.FIRE_WAITER), aw, ah));
        row.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.cooking.demolish"), () -> demolish(), aw, ah));
        return row;
    }

    private static Label label(Component text, Horizontal h, int color, int height, TextWrap wrap) {
        Label l = new Label(); l.setText(text); l.setOverflowVisible(false);
        l.layout(lay -> { lay.widthPercent(100); lay.height(height); });
        l.textStyle(s -> s.textColor(color).textShadow(false).textWrap(wrap).textAlignHorizontal(h).textAlignVertical(Vertical.CENTER));
        return l;
    }

    private static Button flatButton(Component text, Runnable action, int w, int h) {
        Button btn = new Button(); btn.setText(text);
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED).textColor(TEXT_COLOR).textShadow(false));
        btn.setOnClick(e -> action.run());
        btn.buttonStyle(s -> s.baseTexture(new GuiTextureGroup(new ColorRectTexture(BTN_BASE), new ColorBorderTexture(-1, BTN_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BTN_HOVER), new ColorBorderTexture(-1, BTN_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BTN_PRESSED), new ColorBorderTexture(-1, BTN_BORDER))));
        btn.layout(l -> { l.width(w); l.height(h); l.flexShrink(0); });
        return btn;
    }

    private static Button toggleButton(Component hireText, Component fireText, boolean hasWorker, Runnable hireAction, Runnable fireAction, int w, int h) {
        Button btn = new Button();
        btn.setText(hasWorker ? fireText : hireText);
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED).textColor(TEXT_COLOR).textShadow(false));
        btn.setOnClick(e -> (hasWorker ? fireAction : hireAction).run());
        btn.buttonStyle(s -> s.baseTexture(new GuiTextureGroup(new ColorRectTexture(BTN_BASE), new ColorBorderTexture(-1, BTN_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BTN_HOVER), new ColorBorderTexture(-1, BTN_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BTN_PRESSED), new ColorBorderTexture(-1, BTN_BORDER))));
        btn.layout(l -> { l.width(w); l.height(h); l.flexShrink(0); });
        return btn;
    }

    private static Component workerLine(RestaurantControlBoxOpenResponsePacket p) {
        Component chef = p.hasWorker() ? Component.literal(p.workerName()) : Component.translatable("gui.xy2407_nsuk_addition.cooking.none");
        return Component.translatable("gui.xy2407_nsuk_addition.cooking.chef_line", chef);
    }

    private static Component statusLine(RestaurantControlBoxOpenResponsePacket p) {
        Component status = Component.translatable(p.statusKey());
        if (!p.statusText().isBlank()) status = status.copy().append(Component.literal(" " + p.statusText()));
        return Component.translatable("gui.xy2407_nsuk_addition.cooking.status_line", status);
    }

    private static void action(RestaurantControlBoxActionPacket.Action a) {
        if (currentPacket != null) PacketDistributor.sendToServer(new RestaurantControlBoxActionPacket(currentPacket.boxPos(), a, ""));
    }

    private static void hire(String role) {
        if (currentPacket != null) NpcHireScreen.request(currentPacket.boxPos(), RestaurantConstants.HIRE_SOURCE_TYPE, role);
    }

    private static void demolish() {
        if (currentPacket != null) {
            BlockPos pos = currentPacket.boxPos();
            currentPacket = null;
            Minecraft.getInstance().setScreen(null);
            PacketDistributor.sendToServer(new RestaurantControlBoxDemolishPacket(pos));
        }
    }

    private static void close() { currentPacket = null; Minecraft.getInstance().setScreen(null); }

    private static void openMenuSelect(RestaurantControlBoxOpenResponsePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Set<String> selected = new HashSet<>(p.selectedCookItems());
        mc.execute(() -> mc.setScreen(new ModularUIScreen(createMenuSelectUi(p, selected), Component.empty())));
    }

    private static ModularUI createMenuSelectUi(RestaurantControlBoxOpenResponsePacket p, Set<String> selected) {
        int sw = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int sh = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        int pp = clamp(Math.round(Math.min(sw, sh) * 0.018F), 4, 10);
        int pw = clamp(Math.min(300, sw - pp * 2), 240, sw - pp * 2);
        int ph = clamp(Math.min(280, sh - pp * 2 - 24), 200, sh - pp * 2);
        int ip = clamp(Math.round(pw * 0.024F), 6, 12);
        int gap = clamp(Math.round(ph * 0.018F), 3, 6);
        int searchH = 16;
        int btnH = clamp(Math.round(ph * 0.085F), 22, 28);

        UIElement root = new UIElement().layout(l -> { l.widthPercent(100); l.heightPercent(100); l.alignItems(AlignItems.CENTER); l.justifyContent(AlignContent.CENTER); l.paddingAll(pp); });
        root.addChild(SimuKraftUiTheme.createShellPanel(sw, sh));

        UIElement panel = new UIElement().layout(l -> { l.width(pw); l.height(ph); l.paddingAll(ip); l.flexDirection(FlexDirection.COLUMN); l.alignItems(AlignItems.STRETCH); l.gapAll(gap); })
                .style(s -> s.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(PANEL_BG), new ColorBorderTexture(-1, BORDER))));

        int tbH = clamp(Math.round(ph * 0.085F), 18, 24);
        UIElement titleBar = new UIElement().layout(l -> { l.widthPercent(100); l.height(tbH); }).style(s -> s.backgroundTexture(new ColorRectTexture(TITLE_BG)));
        titleBar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.menu_select_title"), Horizontal.CENTER, TEXT_COLOR, tbH, TextWrap.HIDE));
        panel.addChild(titleBar);

        TextField search = new TextField();
        search.setAnyString();
        search.setText("");
        search.textFieldStyle(s -> s.placeholder(Component.translatable("gui.xy2407_nsuk_addition.cooking.search_dish")).textColor(TEXT_COLOR).textShadow(false));
        search.layout(l -> { l.widthPercent(100); l.height(searchH); });

        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.layout(l -> { l.widthPercent(100); l.flex(1); });
        scroller.viewContainer(vc -> vc.layout(l -> { l.widthPercent(100); l.flexDirection(FlexDirection.COLUMN); l.gapAll(2); }));
        scroller.viewPort(vp -> vp.layout(l -> l.paddingAll(2)).style(s -> s.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(PANEL_BG), new ColorBorderTexture(-1, BORDER)))));
        scroller.verticalScroller(vs -> vs.layout(l -> l.width(8)));

        List<DishEntry> dishes = buildDishList(p);
        refreshDishList(scroller, dishes, selected, "");

        search.setTextResponder(text -> refreshDishList(scroller, dishes, selected, text));

        panel.addChild(search);
        panel.addChild(scroller);

        UIElement btnRow = new UIElement().layout(l -> { l.widthPercent(100); l.height(btnH); l.flexDirection(FlexDirection.ROW); l.justifyContent(AlignContent.CENTER); l.gapAll(gap); });
        btnRow.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.cooking.save"), () -> saveMenuSelect(p.boxPos(), selected), pw / 3, btnH));
        btnRow.addChild(flatButton(Component.translatable("gui.button.done"), () -> Minecraft.getInstance().setScreen(null), pw / 3, btnH));
        panel.addChild(btnRow);

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static List<DishEntry> buildDishList(RestaurantControlBoxOpenResponsePacket p) {
        List<DishEntry> list = new ArrayList<>();
        for (RestaurantControlBoxOpenResponsePacket.RecipeEntry recipe : p.recipes()) {
            if (recipe.outputs().isEmpty()) continue;
            String itemId = recipe.outputs().getFirst().itemId();
            ItemStack stack = ItemStack.EMPTY;
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null) {
                var item = BuiltInRegistries.ITEM.get(rl);
                if (item != null) stack = new ItemStack(item);
            }
            Component name = stack.isEmpty() ? Component.literal(itemId) : stack.getHoverName();
            list.add(new DishEntry(itemId, stack, name));
        }
        return list;
    }

    private static void refreshDishList(ScrollerView scroller, List<DishEntry> dishes, Set<String> selected, String query) {
        scroller.clearAllScrollViewChildren();
        String q = query.toLowerCase(Locale.ROOT);
        for (DishEntry dish : dishes) {
            if (!q.isEmpty() && !dish.name().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            scroller.addScrollViewChild(createDishRow(dish, selected));
        }
    }

    private static UIElement createDishRow(DishEntry dish, Set<String> selectedSet) {
        int rowH = 22;
        UIElement row = new UIElement().layout(l -> { l.widthPercent(100); l.height(rowH); l.flexDirection(FlexDirection.ROW); l.alignItems(AlignItems.CENTER); l.gapAll(4); });

        ItemSlot icon = new ItemSlot();
        icon.setItem(dish.stack());
        icon.layout(l -> { l.width(20); l.height(20); l.flexShrink(0); });
        row.addChild(icon);

        Label name = new Label();
        name.setText(dish.name());
        name.layout(l -> { l.flex(1); l.height(rowH); });
        name.textStyle(s -> s.textColor(TEXT_COLOR).textShadow(false).textAlignVertical(Vertical.CENTER).textWrap(TextWrap.HIDE));
        row.addChild(name);

        boolean selected = selectedSet.contains(dish.itemId());
        int bg = selected ? 0xFFC8E6C9 : BTN_BASE;
        int bgHover = selected ? 0xFFB2DFDB : BTN_HOVER;
        Button toggle = new Button();
        toggle.setText(Component.literal(selected ? "✓" : "✗"));
        toggle.textStyle(s -> s.textColor(TEXT_COLOR).textShadow(false));
        toggle.setOnClick(e -> {
            if (selectedSet.contains(dish.itemId())) {
                selectedSet.remove(dish.itemId());
                toggle.setText(Component.literal("✗"));
                toggle.buttonStyle(bs -> bs.baseTexture(new GuiTextureGroup(new ColorRectTexture(BTN_BASE), new ColorBorderTexture(-1, BTN_BORDER))));
            } else {
                selectedSet.add(dish.itemId());
                toggle.setText(Component.literal("✓"));
                toggle.buttonStyle(bs -> bs.baseTexture(new GuiTextureGroup(new ColorRectTexture(0xFFC8E6C9), new ColorBorderTexture(-1, BTN_BORDER))));
            }
        });
        toggle.buttonStyle(s -> s.baseTexture(new GuiTextureGroup(new ColorRectTexture(bg), new ColorBorderTexture(-1, BTN_BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(bgHover), new ColorBorderTexture(-1, BTN_BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BTN_PRESSED), new ColorBorderTexture(-1, BTN_BORDER))));
        toggle.layout(l -> { l.width(24); l.height(rowH); l.flexShrink(0); });
        row.addChild(toggle);
        return row;
    }

    private record DishEntry(String itemId, ItemStack stack, Component name) {}

    private static void saveMenuSelect(BlockPos boxPos, Set<String> selected) {
        PacketDistributor.sendToServer(new RestaurantMenuSelectPacket(boxPos, selected));
        Minecraft.getInstance().setScreen(null);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static final class RestaurantScreen extends ModularUIScreen {
        RestaurantScreen(ModularUI ui, Component title) { super(ui, title); }
        @Override
        public void removed() {
            super.removed();
            if (!(Minecraft.getInstance().screen instanceof RestaurantScreen)) currentPacket = null;
        }
    }
}
