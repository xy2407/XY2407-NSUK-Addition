package com.xy2407.nsukaddition.client.cooking;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
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
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireActionPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireResponsePacket;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** 餐厅女仆雇佣界面：SimuKraft 主题风格，仅列出玩家当前维度内已驯服的女仆(与市民完全隔离)。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RestaurantMaidHireScreenOpener {

    private static final float ROLL_SPEED = 0.25F;

    private RestaurantMaidHireScreenOpener() {}

    private static RestaurantMaidHireResponsePacket currentPacket;

    public static void open(RestaurantMaidHireResponsePacket packet) {
        if (packet == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        currentPacket = packet;
        mc.execute(() -> mc.setScreen(new ModularUIScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(RestaurantMaidHireResponsePacket p) {
        int sw = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int sh = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        int pp = clamp(Math.round(Math.min(sw, sh) * 0.018F), 4, 10);
        int pw = clamp(Math.min(300, sw - pp * 2), 240, sw - pp * 2);
        int ph = clamp(Math.min(320, sh - pp * 2 - 24), 220, sh - pp * 2);
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
        titleBar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.maid_hire_title"), Horizontal.CENTER, 0xFFFFFFFF, tbH, TextWrap.HIDE));
        panel.addChild(titleBar);

        long hiredCount = p.candidates().stream().filter(RestaurantMaidHireResponsePacket.MaidCandidate::hired).count();
        panel.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.maid_count_line", hiredCount),
                Horizontal.LEFT, 0xFFE0E0FF, 12, TextWrap.HIDE));

        TextField search = new TextField();
        search.setAnyString();
        search.setText("");
        search.textFieldStyle(s -> s.placeholder(Component.translatable("gui.xy2407_nsuk_addition.cooking.search_maid")).textColor(0xFFFFFFFF).textShadow(true));
        search.layout(l -> { l.widthPercent(100); l.height(searchH); });

        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.layout(l -> { l.widthPercent(100); l.flex(1); });
        scroller.viewContainer(vc -> vc.layout(l -> { l.widthPercent(100); l.flexDirection(FlexDirection.COLUMN); l.gapAll(2); }));
        scroller.viewPort(vp -> vp.layout(l -> l.paddingAll(2)));
        scroller.verticalScroller(vs -> vs.layout(l -> l.width(8)));

        List<RestaurantMaidHireResponsePacket.MaidCandidate> candidates = p.candidates();
        refreshList(scroller, candidates, p, "");

        search.setTextResponder(text -> refreshList(scroller, candidates, p, text));

        panel.addChild(search);
        panel.addChild(scroller);

        UIElement btnRow = new UIElement().layout(l -> { l.widthPercent(100); l.height(btnH); l.flexDirection(FlexDirection.ROW); l.justifyContent(AlignContent.CENTER); l.gapAll(gap); });
        btnRow.addChild(flatButton(Component.translatable("gui.button.done"), () -> Minecraft.getInstance().setScreen(null), pw / 3, btnH));
        panel.addChild(btnRow);

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static void refreshList(ScrollerView scroller, List<RestaurantMaidHireResponsePacket.MaidCandidate> candidates,
                                    RestaurantMaidHireResponsePacket p, String query) {
        scroller.clearAllScrollViewChildren();
        String q = query.toLowerCase(Locale.ROOT);
        for (RestaurantMaidHireResponsePacket.MaidCandidate c : candidates) {
            if (!q.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            scroller.addScrollViewChild(createRow(c, c.hired(), p.pos()));
        }
        if (candidates.isEmpty() && q.isEmpty()) {
            scroller.addScrollViewChild(label(Component.translatable("gui.xy2407_nsuk_addition.cooking.maid_none_found"),
                    Horizontal.CENTER, 0xFFFF7777, 20, TextWrap.HIDE));
        }
    }

    private static UIElement createRow(RestaurantMaidHireResponsePacket.MaidCandidate candidate, boolean hired, BlockPos pos) {
        int rowH = 22;
        UIElement row = new UIElement().layout(l -> { l.widthPercent(100); l.height(rowH); l.flexDirection(FlexDirection.ROW); l.alignItems(AlignItems.CENTER); l.gapAll(4); });

        ItemSlot icon = new ItemSlot();
        icon.setItem(new ItemStack(Items.POPPY));
        icon.layout(l -> { l.width(20); l.height(20); l.flexShrink(0); });
        row.addChild(icon);

        Label name = new Label();
        name.setText(Component.literal(candidate.name()));
        name.layout(l -> { l.flex(1); l.height(rowH); });
        name.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(true).textAlignVertical(Vertical.CENTER).textWrap(TextWrap.HIDE));
        row.addChild(name);

        Button toggle = new Button();
        toggle.setText(Component.translatable(hired
                ? "gui.xy2407_nsuk_addition.cooking.fire_maid"
                : "gui.xy2407_nsuk_addition.cooking.hire_maid"));
        toggle.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED));
        toggle.setOnClick(e -> {
            PacketDistributor.sendToServer(new RestaurantMaidHireActionPacket(pos,
                    hired ? RestaurantMaidHireActionPacket.Action.FIRE : RestaurantMaidHireActionPacket.Action.HIRE,
                    candidate.uuid()));
            Minecraft.getInstance().setScreen(null);
        });
        toggle.layout(l -> { l.width(60); l.height(rowH); l.flexShrink(0); });
        row.addChild(toggle);
        return row;
    }

    private static Label label(Component text, Horizontal h, int color, int height, TextWrap wrap) {
        Label l = new Label(); l.setText(text); l.setOverflowVisible(false);
        l.layout(lay -> { lay.widthPercent(100); lay.height(height); });
        l.textStyle(s -> s.textColor(color).textShadow(true).textWrap(wrap).textAlignHorizontal(h).textAlignVertical(Vertical.CENTER));
        return l;
    }

    private static Button flatButton(Component text, Runnable action, int w, int h) {
        Button btn = new Button(); btn.setText(text);
        btn.textStyle(s -> s.textWrap(TextWrap.HOVER_ROLL).rollSpeed(ROLL_SPEED));
        btn.setOnClick(e -> action.run());
        btn.layout(l -> { l.width(w); l.height(h); l.flexShrink(0); });
        return btn;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
