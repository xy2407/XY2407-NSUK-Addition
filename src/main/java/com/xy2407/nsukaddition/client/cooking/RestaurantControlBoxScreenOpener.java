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

/** 餐厅控制箱客户端界面：布局与按钮样式对齐 simukraft 工业/商业控制箱(SimuKraftUiTheme LSS 主题)。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RestaurantControlBoxScreenOpener {

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

        int tbH = clamp(Math.round(ph * 0.085F), 18, 24);
        int ilH = clamp(Math.round(ph * 0.060F), 12, 18);
        int acH = clamp(Math.round(ph * 0.085F), 22, 28);
        int acW = clamp((pw - ip * 2 - gap * 2) / 3, 68, 108);
        int dbW = clamp(Math.round(pw * 0.16F), 50, 76);
        int toolW = clamp(Math.round(pw * 0.235F), 86, 112);
        int toolBtnH = clamp(Math.round(ph * 0.085F), 18, 24);

        UIElement root = new UIElement().layout(l -> { l.widthPercent(100); l.heightPercent(100); l.alignItems(AlignItems.CENTER); l.justifyContent(AlignContent.CENTER); l.paddingAll(pp); });
        root.addChild(SimuKraftUiTheme.createShellPanel(sw, sh));

        UIElement panel = new UIElement().layout(l -> { l.width(pw); l.height(ph); l.paddingAll(ip); l.flexDirection(FlexDirection.COLUMN); l.alignItems(AlignItems.STRETCH); l.gapAll(gap); })
                .addClass("simukraft_panel");

        panel.addChild(titleBar(tbH, dbW, packet.autoRestock()));
        panel.addChild(header(packet, ilH, gap, toolW, toolBtnH));
        panel.addChild(menuSelectButton(acH));
        panel.addChild(runToggleButton(acH));
        panel.addChild(bottomRow(packet, acH, acW, gap));

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(int height, int doneWidth, boolean autoRestock) {
        UIElement bar = new UIElement().layout(l -> { l.widthPercent(100); l.height(height); });
        bar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.title"), Horizontal.CENTER, 0xFFFFFFFF, height, TextWrap.HIDE));

        Button close = new Button();
        close.setText(Component.translatable("gui.button.done"));
        close.setOnClick(e -> close());
        close.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(0); l.top(0); l.width(doneWidth); l.height(height); });
        bar.addChild(close);

        int btnW = clamp(Math.round(doneWidth * 0.8F), 40, 60);
        int btnH = Math.max(12, height - 8);
        int lblH = height - btnH;

        Label anchor = new Label();
        anchor.setText(Component.translatable(autoRestock
                ? "gui.xy2407_nsuk_addition.cooking.auto_restock_on"
                : "gui.xy2407_nsuk_addition.cooking.auto_restock_off"));
        anchor.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(true));
        anchor.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(0); l.top(0); l.width(btnW * 2 + 2); l.height(lblH); });

        Button onBtn = new Button();
        onBtn.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.auto_restock_on_btn"));
        Button offBtn = new Button();
        offBtn.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.auto_restock_off_btn"));
        onBtn.setActive(!autoRestock);
        offBtn.setActive(autoRestock);
        onBtn.setOnClick(e -> { action(RestaurantControlBoxActionPacket.Action.TOGGLE_AUTORESTOCK); onBtn.setActive(false); offBtn.setActive(true); });
        offBtn.setOnClick(e -> { action(RestaurantControlBoxActionPacket.Action.TOGGLE_AUTORESTOCK); onBtn.setActive(true); offBtn.setActive(false); });
        onBtn.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(btnW + 2); l.top(lblH); l.width(btnW); l.height(btnH); });
        offBtn.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.right(0); l.top(lblH); l.width(btnW); l.height(btnH); });

        bar.addChild(anchor);
        bar.addChild(onBtn);
        bar.addChild(offBtn);
        return bar;
    }

    private static UIElement header(RestaurantControlBoxOpenResponsePacket p, int lineH, int gap, int toolW, int toolBtnH) {
        UIElement row = new UIElement().layout(l -> { l.widthPercent(100); l.flex(1); l.flexShrink(1); l.flexDirection(FlexDirection.ROW); l.alignItems(AlignItems.STRETCH); l.gapAll(gap); });

        UIElement info = new UIElement().layout(l -> { l.flex(1); l.flexShrink(1); l.flexDirection(FlexDirection.COLUMN); l.alignItems(AlignItems.STRETCH); l.gapAll(gap); });
        info.setOverflowVisible(false);
        info.addChild(label(workerLine(p), Horizontal.LEFT, 0xFFF5F5A0, lineH, TextWrap.HOVER_ROLL));
        boolean isMaidOnly = "maid".equals(p.waiterType());
        boolean isAnd = "and".equals(p.waiterType());
        if (isMaidOnly) {
            info.addChild(label(maidLine(p), Horizontal.LEFT, 0xFFF5F5A0, lineH, TextWrap.HOVER_ROLL));
        } else {
            info.addChild(label(waiterLine(p), Horizontal.LEFT, 0xFFF5F5A0, lineH, TextWrap.HOVER_ROLL));
            if (isAnd) {
                info.addChild(label(maidLine(p), Horizontal.LEFT, 0xFFF5F5A0, lineH, TextWrap.HOVER_ROLL));
            }
        }
        info.addChild(label(statusLine(p), Horizontal.LEFT, 0xFFE0E0FF, lineH, TextWrap.HOVER_ROLL));
        row.addChild(info);

        UIElement tools = new UIElement().layout(l -> { l.width(toolW); l.flexShrink(0); l.flexDirection(FlexDirection.COLUMN); l.alignItems(AlignItems.STRETCH); l.justifyContent(AlignContent.CENTER); l.gapAll(gap); });
        tools.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.cooking.demolish"), () -> demolish(), true, toolW, toolBtnH));
        row.addChild(tools);
        return row;
    }

    private static Component maidLine(RestaurantControlBoxOpenResponsePacket p) {
        String names = p.maidWaiters().isEmpty()
                ? Component.translatable("gui.xy2407_nsuk_addition.cooking.none").getString()
                : p.maidWaiters().stream().map(m -> m.name()).reduce((a, b) -> a + ", " + b).orElse("");
        return Component.translatable("gui.xy2407_nsuk_addition.cooking.maid_line",
                Component.literal(names));
    }

    private static Component waiterLine(RestaurantControlBoxOpenResponsePacket p) {
        Component waiter = p.hasWaiter() ? Component.literal(p.waiterName()) : Component.translatable("gui.xy2407_nsuk_addition.cooking.none");
        return Component.translatable("gui.xy2407_nsuk_addition.cooking.waiter_line", waiter);
    }

    private static UIElement runToggleButton(int height) {
        Button btn = new Button();
        btn.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.toggle"));
        btn.setOnClick(e -> action(RestaurantControlBoxActionPacket.Action.TOGGLE_RUN));
        btn.layout(l -> { l.widthPercent(100); l.height(height); });
        return btn;
    }

    private static UIElement menuSelectButton(int height) {
        Button btn = new Button();
        btn.setText(Component.translatable("gui.xy2407_nsuk_addition.cooking.menu_select"));
        btn.setOnClick(e -> openMenuSelect(currentPacket));
        btn.layout(l -> { l.widthPercent(100); l.height(height); });
        return btn;
    }

    private static UIElement bottomRow(RestaurantControlBoxOpenResponsePacket p, int ah, int aw, int gap) {
        UIElement row = new UIElement().layout(l -> { l.widthPercent(100); l.height(ah); l.flexDirection(FlexDirection.ROW); l.flexWrap(FlexWrap.WRAP); l.justifyContent(AlignContent.CENTER); l.gapAll(gap); });
        row.addChild(toggleButton(
                Component.translatable("gui.xy2407_nsuk_addition.cooking.hire_chef"),
                Component.translatable("gui.xy2407_nsuk_addition.cooking.fire_chef"),
                p.hasWorker(), () -> hire(RestaurantConstants.HIRE_ROLE_CHEF),
                () -> action(RestaurantControlBoxActionPacket.Action.FIRE_CHEF), aw, ah));
        boolean isMaidOnly = "maid".equals(p.waiterType());
        boolean isAnd = "and".equals(p.waiterType());
        if (isMaidOnly) {
            row.addChild(flatButton(
                    Component.translatable("gui.xy2407_nsuk_addition.cooking.manage_maid"),
                    () -> openMaidHire(p.boxPos()), true, aw, ah));
        } else {
            row.addChild(toggleButton(
                    Component.translatable("gui.xy2407_nsuk_addition.cooking.hire_waiter"),
                    Component.translatable("gui.xy2407_nsuk_addition.cooking.fire_waiter"),
                    p.hasWaiter(), () -> hire(RestaurantConstants.HIRE_ROLE_WAITER),
                    () -> action(RestaurantControlBoxActionPacket.Action.FIRE_WAITER), aw, ah));
            if (isAnd) {
                row.addChild(flatButton(
                        Component.translatable("gui.xy2407_nsuk_addition.cooking.manage_maid"),
                        () -> openMaidHire(p.boxPos()), true, aw, ah));
            }
        }
        return row;
    }

    private static void openMaidHire(BlockPos boxPos) {
        if (boxPos == null) return;
        PacketDistributor.sendToServer(new com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireRequestPacket(boxPos));
    }

    private static Label label(Component text, Horizontal h, int color, int height, TextWrap wrap) {
        Label l = new Label(); l.setText(text); l.setOverflowVisible(false);
        l.layout(lay -> { lay.widthPercent(100); lay.height(height); });
        l.textStyle(s -> s.textColor(color).textShadow(true).textWrap(wrap).textAlignHorizontal(h).textAlignVertical(Vertical.CENTER));
        return l;
    }

    private static Button flatButton(Component text, Runnable action, boolean active, int w, int h) {
        Button btn = new Button(); btn.setText(text);
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED));
        if (active) {
            btn.setOnClick(e -> action.run());
        }
        btn.setActive(active);
        btn.layout(l -> { l.width(w); l.height(h); l.flexShrink(0); });
        return btn;
    }

    private static Button toggleButton(Component hireText, Component fireText, boolean hasWorker, Runnable hireAction, Runnable fireAction, int w, int h) {
        Button btn = new Button();
        btn.setText(hasWorker ? fireText : hireText);
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED));
        btn.setOnClick(e -> (hasWorker ? fireAction : hireAction).run());
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
                .addClass("simukraft_panel");

        int tbH = clamp(Math.round(ph * 0.085F), 18, 24);
        UIElement titleBar = new UIElement().layout(l -> { l.widthPercent(100); l.height(tbH); });
        titleBar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.menu_select_title"), Horizontal.CENTER, 0xFFFFFFFF, tbH, TextWrap.HIDE));
        panel.addChild(titleBar);

        TextField search = new TextField();
        search.setAnyString();
        search.setText("");
        search.textFieldStyle(s -> s.placeholder(Component.translatable("gui.xy2407_nsuk_addition.cooking.search_dish")).textColor(0xFFFFFFFF).textShadow(true));
        search.layout(l -> { l.widthPercent(100); l.height(searchH); });

        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.layout(l -> { l.widthPercent(100); l.flex(1); });
        scroller.viewContainer(vc -> vc.layout(l -> { l.widthPercent(100); l.flexDirection(FlexDirection.COLUMN); l.gapAll(2); }));
        scroller.viewPort(vp -> vp.layout(l -> l.paddingAll(2)));
        scroller.verticalScroller(vs -> vs.layout(l -> l.width(8)));

        List<DishEntry> dishes = buildDishList(p);
        refreshDishList(scroller, dishes, selected, "");

        search.setTextResponder(text -> refreshDishList(scroller, dishes, selected, text));

        panel.addChild(search);
        panel.addChild(scroller);

        UIElement btnRow = new UIElement().layout(l -> { l.widthPercent(100); l.height(btnH); l.flexDirection(FlexDirection.ROW); l.justifyContent(AlignContent.CENTER); l.gapAll(gap); });
        btnRow.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.cooking.save"), () -> saveMenuSelect(p.boxPos(), selected), true, pw / 3, btnH));
        btnRow.addChild(flatButton(Component.translatable("gui.button.done"), () -> Minecraft.getInstance().setScreen(null), true, pw / 3, btnH));
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
        name.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(true).textAlignVertical(Vertical.CENTER).textWrap(TextWrap.HIDE));
        row.addChild(name);

        boolean selected = selectedSet.contains(dish.itemId());
        Button toggle = new Button();
        toggle.setText(Component.literal(selected ? "✓" : "✗"));
        toggle.setOnClick(e -> {
            if (selectedSet.contains(dish.itemId())) {
                selectedSet.remove(dish.itemId());
                toggle.setText(Component.literal("✗"));
            } else {
                selectedSet.add(dish.itemId());
                toggle.setText(Component.literal("✓"));
            }
        });
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
