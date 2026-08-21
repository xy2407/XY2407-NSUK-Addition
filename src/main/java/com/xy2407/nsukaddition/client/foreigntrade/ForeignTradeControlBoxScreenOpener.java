package com.xy2407.nsukaddition.client.foreigntrade;

import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenResponsePacket;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** 外贸控制箱客户端界面，选择建交城市后打开对应市场。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ForeignTradeControlBoxScreenOpener {

    private static final int TEXT_COLOR = 0xFF1A1A1A;
    private static final int TEXT_ACCENT_COLOR = 0xFF333333;
    private static final int TEXT_STATUS_COLOR = 0xFF555555;
    private static final int TITLE_BAR_BG = 0xFFE0E0E0;
    private static final float TEXT_ROLL_SPEED = 0.25F;

    private static BlockPos openedBoxPos;
    private static ForeignTradeControlBoxOpenResponsePacket currentPacket;
    private static String selectedCityId;
    private static String searchText = "";
    private static ScrollerView currentScroll;

    private ForeignTradeControlBoxScreenOpener() {}

    public static BlockPos getOpenedBoxPos() { return openedBoxPos; }

    public static void open(ForeignTradeControlBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        openedBoxPos = packet.boxPos().immutable();
        currentPacket = packet;
        mc.execute(() -> mc.setScreen(new ModularUIScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(ForeignTradeControlBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        int panelWidth = Math.min(360, screenWidth - 20);
        int panelHeight = Math.min(280, screenHeight - 20);

        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(panelWidth);
            layout.height(panelHeight);
            layout.paddingAll(8);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(4);
        }).addClass("simukraft_panel");

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(20);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(TITLE_BAR_BG)));
        titleBar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.title"),
                Horizontal.CENTER, TEXT_COLOR, 20, TextWrap.HIDE));
        panel.addChild(titleBar);

        if (selectedCityId != null) {
            for (DiplomacyRelation rel : DiplomacyClientCache.getRelations()) {
                if (rel.cityId().equals(selectedCityId)) {
                    String cityName = rel.cityName() != null && !rel.cityName().isEmpty()
                            ? rel.cityName() : selectedCityId;
                    panel.addChild(label(Component.translatable(
                            "gui.xy2407_nsuk_addition.foreign_trade.selected_market", cityName),
                            Horizontal.LEFT, TEXT_ACCENT_COLOR, 16, TextWrap.HOVER_ROLL));
                    break;
                }
            }
        }

        TextField search = new TextField();
        search.setText(searchText);
        search.setTextResponder(newText -> {
            searchText = newText == null ? "" : newText;
            refreshCards();
        });
        search.layout(layout -> { layout.widthPercent(100); layout.height(20); layout.paddingHorizontal(4); });
        search.textFieldStyle(style -> style.cursorColor(0xFF000000));
        panel.addChild(search);

        panel.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.select_village"),
                Horizontal.LEFT, TEXT_ACCENT_COLOR, 16, TextWrap.HIDE));

        currentScroll = new ScrollerView();
        currentScroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        currentScroll.viewContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
        refreshCards();
        panel.addChild(currentScroll);

        UIElement bottomRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(4);
        });

        boolean canOpenMarket = selectedCityId != null;
        Button marketBtn = new Button();
        marketBtn.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.open_market"));
        marketBtn.setOnClick(event -> ForeignTradeMenuScreenOpener.open(packet.boxPos(), selectedCityId));
        marketBtn.setActive(canOpenMarket);
        marketBtn.layout(layout -> { layout.flex(1); layout.height(24); });
        bottomRow.addChild(marketBtn);

        Button demolishBtn = new Button();
        demolishBtn.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.demolish"));
        demolishBtn.setOnClick(event -> demolish(packet));
        demolishBtn.layout(layout -> { layout.flex(1); layout.height(24); });
        bottomRow.addChild(demolishBtn);

        panel.addChild(bottomRow);
        root.addChild(panel);

        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static void refreshCards() {
        if (currentScroll == null) return;
        UIElement view = currentScroll.viewContainer;
        view.clearAllChildren();
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        List<DiplomacyRelation> rels = DiplomacyClientCache.getRelations().stream()
                .filter(r -> q.isEmpty() || (r.cityName() != null && r.cityName().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
        if (rels.isEmpty()) {
            view.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.no_diplomacy"),
                    Horizontal.LEFT, TEXT_STATUS_COLOR, 16, TextWrap.HIDE));
            return;
        }
        for (DiplomacyRelation rel : rels) {
            String cityId = rel.cityId();
            String cityName = rel.cityName();
            boolean selected = cityId.equals(selectedCityId);
            boolean playerCity = rel.villageType() == null || rel.villageType().isBlank();
            String marker = selected ? "► " : (playerCity ? "⭐ " : "");
            Button card = new Button();
            card.setText(Component.literal(marker + (cityName != null && !cityName.isEmpty() ? cityName : cityId)));
            card.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
            card.setOnClick(event -> {
                selectedCityId = cityId;
                if (currentPacket != null) open(currentPacket);
            });
            card.layout(layout -> { layout.widthPercent(100); layout.height(24); });
            view.addChild(card);
        }
    }

    private static Label label(Component text, Horizontal hAlign, int color, int height, TextWrap wrap) {
        Label lbl = new Label();
        lbl.setText(text);
        lbl.textStyle(style -> style.textColor(color).textShadow(false)
                .textAlignHorizontal(hAlign).textAlignVertical(Vertical.CENTER));
        lbl.layout(layout -> { layout.widthPercent(100); layout.height(height); });
        return lbl;
    }

    private static void demolish(ForeignTradeControlBoxOpenResponsePacket packet) {
        openedBoxPos = null;
        currentPacket = null;
        selectedCityId = null;
        Minecraft.getInstance().setScreen(null);
        PacketDistributor.sendToServer(new ForeignTradeControlBoxDemolishPacket(packet.boxPos()));
    }

    private static void close() {
        openedBoxPos = null;
        currentPacket = null;
        selectedCityId = null;
        Minecraft.getInstance().setScreen(null);
    }
}
