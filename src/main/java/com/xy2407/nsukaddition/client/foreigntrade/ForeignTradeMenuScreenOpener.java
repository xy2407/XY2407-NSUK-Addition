package com.xy2407.nsukaddition.client.foreigntrade;

import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.foreigntrade.TradeItemResolver;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import com.xy2407.nsukaddition.common.network.foreigntrade.*;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 全屏外贸菜单GUI，含搜索框、分类栏、卡片网格和自由市场模式。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ForeignTradeMenuScreenOpener {

    private static final int BG = 0xFFF5F5F5;
    private static final int BORDER = 0xFF000000;
    private static final int CARD_BG = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFF1A1A1A;
    private static final int SELL_PRICE = 0xFF5B9BD5;
    private static final int BUY_PRICE = 0xFF70AD47;
    private static final int SELL_BTN = 0xFF5B9BD5;
    private static final int BUY_BTN = 0xFF70AD47;
    private static final int CANCEL_BTN = 0xFFD9534F;
    private static final int MODIFY_BTN = 0xFFF0AD4E;
    private static final int BTN_HOVER_DARK = 0xFF444444;
    private static final int TITLE_BAR_BG = 0xFFE0E0E0;
    private static final int SEARCH_BG = 0xFFFFFFFF;
    private static final int SCROLLBAR_TRACK = 0xFFD0D0D0;
    private static final int SCROLLBAR_THUMB = 0xFF808080;
    private static final int SCROLLBAR_THUMB_HOVER = 0xFF606060;
    private static final int OWNED_COLOR = 0xFF666666;
    private static final int CITY_LABEL_COLOR = 0xFF888888;
    private static final float TEXT_ROLL_SPEED = 0.25F;

    private static final int CATEGORY_WIDTH = 50;
    private static final int CATEGORY_ITEM_H = 24;
    private static final int CATEGORY_SELECTED_BG = 0xFF555555;
    private static final int CATEGORY_NORMAL_BG = 0xFFCCCCCC;
    private static final int CATEGORY_HOVER_BG = 0xFFBBBBBB;
    private static final int CATEGORY_SELECTED_TEXT = 0xFFFFFFFF;
    private static final int CATEGORY_NORMAL_TEXT = 0xFF333333;

    private static final int CARD_SIZE = 96;
    private static final int CARD_GAP = 8;
    private static final int ICON_SIZE = 32;
    private static final int PRICE_H = 11;
    private static final int OWNED_H = 10;
    private static final int BTN_H = 18;
    private static final int SEARCH_H = 22;
    private static final int TITLE_BAR_H = 24;
    private static final int TOP_GAP = 18;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_MARGIN = 2;
    private static final int CITY_LABEL_H = 12;

    private static final String[][] CATEGORIES = {
            {"all", "gui.xy2407_nsuk_addition.foreign_trade.category.all"},
            {"wood", "gui.xy2407_nsuk_addition.foreign_trade.category.wood"},
            {"stone", "gui.xy2407_nsuk_addition.foreign_trade.category.stone"},
            {"brick", "gui.xy2407_nsuk_addition.foreign_trade.category.brick"},
            {"sand", "gui.xy2407_nsuk_addition.foreign_trade.category.sand"},
            {"prismarine", "gui.xy2407_nsuk_addition.foreign_trade.category.prismarine"},
            {"quartz", "gui.xy2407_nsuk_addition.foreign_trade.category.quartz"},
            {"wool", "gui.xy2407_nsuk_addition.foreign_trade.category.wool"},
            {"terracotta", "gui.xy2407_nsuk_addition.foreign_trade.category.terracotta"},
            {"concrete", "gui.xy2407_nsuk_addition.foreign_trade.category.concrete"},
            {"glass", "gui.xy2407_nsuk_addition.foreign_trade.category.glass"},
            {"leaves", "gui.xy2407_nsuk_addition.foreign_trade.category.leaves"},
            {"lighting", "gui.xy2407_nsuk_addition.foreign_trade.category.lighting"},
            {"crop", "gui.xy2407_nsuk_addition.foreign_trade.category.crop"},
            {"mineral", "gui.xy2407_nsuk_addition.foreign_trade.category.mineral"},
            {"wine", "gui.xy2407_nsuk_addition.foreign_trade.category.wine"},
            {"free_sell", "gui.xy2407_nsuk_addition.foreign_trade.category.free_sell"},
            {"free_buy", "gui.xy2407_nsuk_addition.foreign_trade.category.free_buy"},
    };

    private static BlockPos boxPos;
    private static String searchText = "";
    private static List<ForeignTradeMarket.MarketEntry> marketData = new ArrayList<>();
    private static List<ForeignTradeMarket.MarketEntry> filtered = new ArrayList<>();
    private static Map<String, Integer> availableCounts = new HashMap<>();

    private static int selectedCategoryIndex = 0;
    private static int categoryScrollOffset = 0;

    private static List<FreeMarketRepository.FreeMarketListing> freeSellListings = new ArrayList<>();
    private static List<FreeMarketRepository.FreeMarketListing> freeBuyListings = new ArrayList<>();

    private static boolean canOperate;

    private static final int BP_W = 180;
    private static final int BP_H = 130;
    private static final int BP_INPUT_W = 80;
    private static final int BP_INPUT_H = 16;
    private static final int BP_BTN_W = 60;
    private static final int BP_BTN_H = 18;
    private static boolean batchPopupVisible;
    private static String batchItemId;
    private static String batchCityId;
    private static int batchUnitCount;
    private static boolean batchIsBuy;
    private static String batchQuantityText = "1";
    private static int batchActiveField;
    private static int batchCursorTick;
    private static int batchAvailableCount;

    private static int scrollOffset;
    private static boolean draggingScrollbar;
    private static int viewportHeight;
    private static int contentHeight;
    private static int gridStartY;
    private static int gridWidth;
    private static int cols;
    private static int rows;
    private static int visibleRows;

    private static final int WP_WIDTH = 200;
    private static final int WP_HEIGHT = 350;
    private static final int WP_TITLE_H = 20;
    private static final int WP_SLOT_SIZE = 20;
    private static final int WP_COLS = 9;
    private static final int WP_CLOSE_SIZE = 12;
    private static final int WP_INPUT_W = 100;
    private static final int WP_INPUT_H = 16;
    private static final int WP_BTN_W = 70;
    private static final int WP_BTN_H = 18;
    private static final int WP_INV_TITLE_H = 12;
    private static final int WP_INV_ROWS = 4;
    private static final int WP_INV_AREA_H = WP_INV_TITLE_H + WP_INV_ROWS * WP_SLOT_SIZE;
    private static final int WP_SEP_H = 4;
    private static final int WP_WAREHOUSE_GRID_H = WP_HEIGHT - WP_TITLE_H - WP_INV_AREA_H - WP_SEP_H;
    private static boolean warehousePanelVisible;
    private static int warehousePanelX = 60, warehousePanelY = 40;
    private static boolean draggingWarehousePanel;
    private static int dragStartX, dragStartY, panelStartX, panelStartY;
    private static List<FreeMarketWarehouseRequestPacket.WarehouseItem> warehouseItems = new ArrayList<>();
    private static String warehouseSelectedItem = null;
    private static String warehouseSelectedNbt = "";
    private static String warehouseCountText = "1";
    private static String warehousePriceText = "10";
    private static boolean warehousePricingMode;
    private static int warehouseActiveField;
    private static int warehouseScrollOffset;
    private static int warehouseCursorTick;

    private static final int MP_W = 200;
    private static final int MP_H = 140;
    private static final int MP_INPUT_W = 100;
    private static final int MP_INPUT_H = 16;
    private static final int MP_BTN_W = 70;
    private static final int MP_BTN_H = 18;
    private static boolean modifyPopupVisible;
    private static long modifyListingId;
    private static String modifyItemId;
    private static String modifyItemNbt;
    private static String modifyCountText;
    private static String modifyPriceText;
    private static int modifyActiveField;
    private static int modifyCursorTick;

    private static UIElement gridContainer;
    private static ForeignTradeMenuScreen currentScreen;
    private static String currentCityId;

    private ForeignTradeMenuScreenOpener() {}

    public static void open(BlockPos pos, String cityId) {
        boxPos = pos != null ? pos.immutable() : null;
        currentCityId = cityId;
        PacketDistributor.sendToServer(new ForeignTradeMarketRequestPacket(boxPos));
    }

    public static void openWithMarketData(BlockPos pos, List<ForeignTradeMarket.MarketEntry> entries, boolean canOperateFlag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        boxPos = pos != null ? pos.immutable() : null;
        marketData = entries;
        String villageType = currentCityId != null ? DiplomacyClientCache.getVillageTypeByCityId(currentCityId) : null;
        var villageFiltered = villageType != null
                ? entries.stream().filter(e -> villageType.equals(e.villageType())).toList()
                : entries;
        filtered = new ArrayList<>(villageFiltered);
        searchText = "";
        scrollOffset = 0;
        categoryScrollOffset = 0;
        selectedCategoryIndex = 0;
        draggingScrollbar = false;
        canOperate = canOperateFlag;
        freeSellListings = new ArrayList<>();
        freeBuyListings = new ArrayList<>();
        warehouseItems = new ArrayList<>();
        mc.execute(() -> {
            currentScreen = new ForeignTradeMenuScreen(createUi(), Component.empty());
            mc.setScreen(currentScreen);
        });
    }

    public static void updateAvailableCounts(Map<String, Integer> counts) {
        availableCounts = counts != null ? counts : new HashMap<>();
        refreshGridCards();
    }

    public static void updateFreeMarketData(
            List<FreeMarketRepository.FreeMarketListing> ownListings,
            List<FreeMarketRepository.FreeMarketListing> otherListings) {
        freeSellListings = ownListings != null ? ownListings : new ArrayList<>();
        freeBuyListings = otherListings != null ? otherListings : new ArrayList<>();
        refreshGridCards();
    }

    private static String currentCategoryKey() {
        return CATEGORIES[selectedCategoryIndex][0];
    }

    private static boolean isFreeSellCategory() {
        return "free_sell".equals(currentCategoryKey());
    }

    private static boolean isFreeBuyCategory() {
        return "free_buy".equals(currentCategoryKey());
    }

    private static boolean isFreeMarketCategory() {
        return isFreeSellCategory() || isFreeBuyCategory();
    }

    private static int getItemCount() {
        if (isFreeSellCategory()) return freeSellListings.size();
        if (isFreeBuyCategory()) return freeBuyListings.size();
        return filtered.size();
    }

    private static int getEffectiveCardH() {
        return isFreeBuyCategory() ? CARD_SIZE + CITY_LABEL_H : CARD_SIZE;
    }

    private static int getEffectiveGap() {
        return isFreeBuyCategory() ? CARD_GAP * 2 : CARD_GAP;
    }

    private static ModularUI createUi() {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        UIElement root = new UIElement().layout(layout -> {
            layout.width(sw);
            layout.height(sh);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(BG)));

        root.addChild(titleBar());
        root.addChild(searchBar());
        root.addChild(scrollableArea(sw, sh));

        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar() {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(TITLE_BAR_H);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(TITLE_BAR_BG)));

        Button backBtn = new Button();
        backBtn.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.back"));
        backBtn.setOnClick(event -> goBack());
        backBtn.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(TEXT_COLOR).textShadow(false));
        backBtn.buttonStyle(style -> style
                .baseTexture(new ColorRectTexture(TITLE_BAR_BG))
                .hoverTexture(new ColorRectTexture(0xFFD0D0D0))
        );
        backBtn.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(4);
            layout.top(3);
            layout.width(40);
            layout.height(18);
        });
        bar.addChild(backBtn);

        Label title = new Label();
        title.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.menu_title"));
        title.setOverflowVisible(false);
        title.layout(layout -> {
            layout.widthPercent(100);
            layout.height(TITLE_BAR_H);
        });
        title.textStyle(style -> style
                .textColor(TEXT_COLOR).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        bar.addChild(title);

        Label hintLabel = new Label();
        hintLabel.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch_hint"));
        hintLabel.setOverflowVisible(false);
        hintLabel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(4);
            layout.top(3);
            layout.height(18);
            layout.width(120);
        });
        hintLabel.textStyle(style -> style
                .textColor(CITY_LABEL_COLOR).textShadow(false)
                .textAlignVertical(Vertical.CENTER)
                .textAlignHorizontal(Horizontal.RIGHT));
        bar.addChild(hintLabel);

        return bar;
    }

    private static UIElement searchBar() {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(SEARCH_H + 16);
            layout.paddingHorizontal(12);
            layout.paddingTop(6);
            layout.paddingBottom(6);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });

        Label searchLabel = new Label();
        searchLabel.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.search"));
        searchLabel.setOverflowVisible(false);
        searchLabel.layout(layout -> {
            layout.widthAuto();
            layout.height(SEARCH_H);
            layout.marginRight(6);
        });
        searchLabel.textStyle(style -> style.textColor(TEXT_COLOR).textShadow(false)
                .textAlignVertical(Vertical.CENTER));
        row.addChild(searchLabel);

        TextField field = new TextField();
        field.setText(searchText);
        field.setTextResponder(newText -> {
            searchText = newText;
            refreshFiltered();
        });
        field.layout(layout -> {
            layout.width(220);
            layout.height(SEARCH_H);
            layout.paddingHorizontal(4);
        });
        field.style(style -> style.backgroundTexture(
                new GuiTextureGroup(new ColorRectTexture(SEARCH_BG), new ColorBorderTexture(1, BORDER)))
        );
        field.textFieldStyle(style -> style.cursorColor(0xFF000000));
        row.addChild(field);

        return row;
    }

    private static UIElement scrollableArea(int sw, int sh) {
        int fixedTop = TITLE_BAR_H + SEARCH_H + 16;
        viewportHeight = sh - fixedTop;
        int scrollbarSpace = SCROLLBAR_WIDTH + SCROLLBAR_MARGIN * 2;
        gridWidth = sw - CATEGORY_WIDTH - scrollbarSpace - TOP_GAP * 2;
        cols = Math.max(1, (gridWidth + getEffectiveGap()) / (CARD_SIZE + getEffectiveGap()));
        rows = (getItemCount() + cols - 1) / cols;
        contentHeight = rows * (getEffectiveCardH() + getEffectiveGap()) + TOP_GAP + getEffectiveCardH();
        visibleRows = Math.max(1, viewportHeight / (getEffectiveCardH() + getEffectiveGap()));
        gridStartY = fixedTop;

        gridContainer = new UIElement() {
            @Override
            public void drawBackgroundAdditional(GUIContext ctx) {
                drawScrollbar(ctx);
                drawCategoryBar(ctx);
                drawCityLabels(ctx);
            }
        };
        gridContainer.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.alignContent(AlignContent.FLEX_START);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(getEffectiveGap());
            layout.paddingAll(TOP_GAP);
            layout.paddingRight(CATEGORY_WIDTH + scrollbarSpace + TOP_GAP);
        });

        addVisibleCards();
        return gridContainer;
    }

    private static void drawScrollbar(GUIContext ctx) {
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0) return;

        GuiGraphics gg = ctx.graphics;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int trackX = sw - CATEGORY_WIDTH - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int trackY = gridStartY;
        int trackH = viewportHeight;

        gg.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, SCROLLBAR_TRACK);

        int thumbH = Math.max(20, (int) ((float) viewportHeight / contentHeight * trackH));
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = trackY + (int) (scrollRatio * (trackH - thumbH));
        int thumbColor = draggingScrollbar ? SCROLLBAR_THUMB_HOVER : SCROLLBAR_THUMB;
        gg.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, thumbColor);
    }

    private static void drawCategoryBar(GUIContext ctx) {
        GuiGraphics gg = ctx.graphics;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int barX = sw - CATEGORY_WIDTH;
        int barY = gridStartY;
        int barH = viewportHeight;

        gg.fill(barX, barY, barX + CATEGORY_WIDTH, barY + barH, 0xFFD8D8D8);
        gg.fill(barX, barY, barX + 1, barY + barH, 0xFF999999);

        var font = Minecraft.getInstance().font;
        int totalH = CATEGORIES.length * CATEGORY_ITEM_H;
        int maxCatScroll = Math.max(0, totalH - barH);
        categoryScrollOffset = Math.min(categoryScrollOffset, maxCatScroll);

        for (int i = 0; i < CATEGORIES.length; i++) {
            int itemY = barY + i * CATEGORY_ITEM_H - categoryScrollOffset;
            if (itemY + CATEGORY_ITEM_H < barY || itemY > barY + barH) continue;

            int drawY = Math.max(itemY, barY);
            int drawH = Math.min(itemY + CATEGORY_ITEM_H, barY + barH) - drawY;
            if (drawH <= 0) continue;

            boolean selected = i == selectedCategoryIndex;
            double mx = Minecraft.getInstance().mouseHandler.xpos() * sw / Minecraft.getInstance().getWindow().getScreenWidth();
            double my = Minecraft.getInstance().mouseHandler.ypos() * barH / Minecraft.getInstance().getWindow().getScreenHeight();
            int scaledHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            double mouseYAbs = Minecraft.getInstance().mouseHandler.ypos() * scaledHeight / Minecraft.getInstance().getWindow().getScreenHeight();
            boolean hovered = mx >= barX && mx < barX + CATEGORY_WIDTH
                    && mouseYAbs >= drawY && mouseYAbs < drawY + drawH && !selected;

            int bgColor = selected ? CATEGORY_SELECTED_BG : (hovered ? CATEGORY_HOVER_BG : CATEGORY_NORMAL_BG);
            gg.fill(barX + 1, drawY, barX + CATEGORY_WIDTH, drawY + drawH, bgColor);

            String name = Component.translatable(CATEGORIES[i][1]).getString();
            int textColor = selected ? CATEGORY_SELECTED_TEXT : CATEGORY_NORMAL_TEXT;
            int textY = itemY + (CATEGORY_ITEM_H - 8) / 2;
            if (textY >= barY && textY + 8 <= barY + barH) {
                int textW = font.width(name);
                int maxTextW = CATEGORY_WIDTH - 4;
                if (textW <= maxTextW) {
                    gg.drawCenteredString(font, name, barX + CATEGORY_WIDTH / 2, textY, textColor);
                } else {
                    float scale = (float) maxTextW / textW;
                    var pose = gg.pose();
                    pose.pushPose();
                    pose.translate(barX + 2, textY, 0);
                    pose.scale(scale, 1.0F, 1.0F);
                    gg.drawString(font, name, 0, 0, textColor, false);
                    pose.popPose();
                }
            }
        }
    }

    private static void drawCityLabels(GUIContext ctx) {
        if (!isFreeBuyCategory() || freeBuyListings.isEmpty()) return;
    }

    private static void addVisibleCards() {
        if (gridContainer == null) return;
        gridContainer.clearAllChildren();

        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int step = getEffectiveCardH() + getEffectiveGap();
        int startRow = scrollOffset / step;
        int endRow = Math.min(rows, startRow + visibleRows + 1);

        if (isFreeSellCategory()) {
            for (int r = startRow; r < endRow; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    if (idx >= freeSellListings.size()) break;
                    gridContainer.addChild(freeMarketCard(freeSellListings.get(idx), true));
                }
            }
        } else if (isFreeBuyCategory()) {
            for (int r = startRow; r < endRow; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    if (idx >= freeBuyListings.size()) break;
                    gridContainer.addChild(freeMarketCard(freeBuyListings.get(idx), false));
                }
            }
        } else {
            for (int r = startRow; r < endRow; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    if (idx >= filtered.size()) break;
                    gridContainer.addChild(tradeCard(filtered.get(idx)));
                }
            }
        }
        gridContainer.markTaffyStyleDirty();
    }

    private static void refreshFiltered() {
        String categoryKey = currentCategoryKey();
        String q = searchText.toLowerCase(Locale.ROOT).trim();

        if (categoryKey.equals("all")) {
            filtered = new ArrayList<>(marketData);
        } else if (isFreeMarketCategory()) {
            filtered = new ArrayList<>();
            if (boxPos != null) {
                PacketDistributor.sendToServer(new FreeMarketDataRequestPacket(boxPos));
            }
        } else {
            filtered = marketData.stream()
                    .filter(e -> e.category().equals(categoryKey))
                    .toList();
        }

        if (!isFreeMarketCategory() && !q.isEmpty()) {
            filtered = filtered.stream()
                    .filter(e -> {
                        ResourceLocation rl = ResourceLocation.tryParse(e.itemId());
                        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
                        String displayName = item != null ? item.getDescription().getString() : e.itemId();
                        return displayName.toLowerCase(Locale.ROOT).contains(q)
                                || e.itemId().toLowerCase(Locale.ROOT).contains(q);
                    })
                    .toList();
        }

        scrollOffset = 0;
        recalcLayout();
        addVisibleCards();
    }

    private static void refreshByCategory() {
        String categoryKey = currentCategoryKey();
        if (categoryKey.equals("all")) {
            filtered = new ArrayList<>(marketData);
        } else if (isFreeMarketCategory()) {
            filtered = new ArrayList<>();
            if (boxPos != null) {
                PacketDistributor.sendToServer(new FreeMarketDataRequestPacket(boxPos));
            }
        } else {
            filtered = marketData.stream()
                    .filter(e -> e.category().equals(categoryKey))
                    .toList();
        }

        String q = searchText.toLowerCase(Locale.ROOT).trim();
        if (!isFreeMarketCategory() && !q.isEmpty()) {
            filtered = filtered.stream()
                    .filter(e -> {
                        ResourceLocation rl = ResourceLocation.tryParse(e.itemId());
                        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
                        String displayName = item != null ? item.getDescription().getString() : e.itemId();
                        return displayName.toLowerCase(Locale.ROOT).contains(q)
                                || e.itemId().toLowerCase(Locale.ROOT).contains(q);
                    })
                    .toList();
        }

        scrollOffset = 0;
        recalcLayout();
        addVisibleCards();
    }

    private static void recalcLayout() {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int scrollbarSpace = SCROLLBAR_WIDTH + SCROLLBAR_MARGIN * 2;
        gridWidth = sw - CATEGORY_WIDTH - scrollbarSpace - TOP_GAP * 2;
        cols = Math.max(1, (gridWidth + getEffectiveGap()) / (CARD_SIZE + getEffectiveGap()));
        rows = (getItemCount() + cols - 1) / cols;
        contentHeight = rows * (getEffectiveCardH() + getEffectiveGap()) + TOP_GAP + getEffectiveCardH();
        visibleRows = Math.max(1, viewportHeight / (getEffectiveCardH() + getEffectiveGap()));
    }

    private static void refreshGridCards() {
        recalcLayout();
        addVisibleCards();
    }

    private static UIElement tradeCard(ForeignTradeMarket.MarketEntry entry) {
        ItemStack stack = TradeItemResolver.buildDisplay(entry.itemId(), entry.category(), 1);

        UIElement card = new UIElement() {
            @Override
            public void drawBackgroundAdditional(GUIContext ctx) {
                if (stack.isEmpty()) return;
                float contentW = getSizeWidth() - 8;
                int itemX = Math.round(getPositionX() + 4 + (contentW - ICON_SIZE) / 2.0f) + 8;
                int itemY = Math.round(getPositionY() + 22);
                ctx.graphics.renderItem(stack, itemX, itemY);
            }
        };

        card.layout(layout -> {
            layout.width(CARD_SIZE);
            layout.height(CARD_SIZE);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.paddingAll(4);
        }).style(style -> style.backgroundTexture(
                new GuiTextureGroup(new ColorRectTexture(CARD_BG), new ColorBorderTexture(1, BORDER)))
        );

        Label name = new Label();
        name.setText(stack.isEmpty() ? Component.literal(entry.itemId()) : stack.getHoverName());
        name.setOverflowVisible(false);
        name.layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
        });
        name.textStyle(style -> style
                .textColor(TEXT_COLOR).textShadow(false).textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        card.addChild(name);

        UIElement iconSpace = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(ICON_SIZE + 2);
        });
        card.addChild(iconSpace);

        int owned = availableCounts.getOrDefault(entry.itemId(), 0);
        Label ownedLabel = new Label();
        ownedLabel.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.owned", owned));
        ownedLabel.setOverflowVisible(false);
        ownedLabel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(OWNED_H);
        });
        ownedLabel.textStyle(style -> style.textColor(OWNED_COLOR).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        card.addChild(ownedLabel);

        UIElement priceRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(PRICE_H);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(2);
        });

        Label sellPrice = new Label();
        sellPrice.setText(Component.literal(formatPrice(entry.sellPrice())));
        sellPrice.setOverflowVisible(false);
        sellPrice.layout(layout -> { layout.flex(1); layout.height(PRICE_H); });
        sellPrice.textStyle(style -> style.textColor(SELL_PRICE).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        priceRow.addChild(sellPrice);

        Label buyPrice = new Label();
        buyPrice.setText(Component.literal(formatPrice(entry.buyPrice())));
        buyPrice.setOverflowVisible(false);
        buyPrice.layout(layout -> { layout.flex(1); layout.height(PRICE_H); });
        buyPrice.textStyle(style -> style.textColor(BUY_PRICE).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        priceRow.addChild(buyPrice);

        card.addChild(priceRow);

        if (canOperate) {
            UIElement btnRow = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(BTN_H);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(2);
            });

            btnRow.addChild(makeSellBuyButton(
                    Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.sell"),
                    SELL_BTN, () -> sendTransaction(currentCityId, entry.itemId(), false, 1),
                    () -> openBatchPopup(currentCityId, entry.itemId(), false, entry.count())));
            btnRow.addChild(makeSellBuyButton(
                    Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.buy"),
                    BUY_BTN, () -> sendTransaction(currentCityId, entry.itemId(), true, 1),
                    () -> openBatchPopup(currentCityId, entry.itemId(), true, entry.count())));

            card.addChild(btnRow);
        }
        return card;
    }

    private static UIElement freeMarketCard(FreeMarketRepository.FreeMarketListing listing, boolean isSellMode) {
        ItemStack stack = ItemStack.EMPTY;
        String itemNbt = listing.itemNbt();
        if (itemNbt != null && !itemNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(itemNbt);
                stack = ItemStack.parseOptional(
                        Minecraft.getInstance().level.registryAccess(),
                        (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(listing.itemId());
            Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        final ItemStack finalStack = stack;

        int cardH = isFreeBuyCategory() ? CARD_SIZE + CITY_LABEL_H : CARD_SIZE;

        UIElement card = new UIElement() {
            @Override
            public void drawBackgroundAdditional(GUIContext ctx) {
                if (finalStack.isEmpty()) return;
                float contentW = getSizeWidth() - 8;
                int itemX = Math.round(getPositionX() + 4 + (contentW - ICON_SIZE) / 2.0f) + 8;
                int itemY = Math.round(getPositionY() + 22);
                ctx.graphics.renderItem(finalStack, itemX, itemY);

                if (!isSellMode && listing.cityName() != null && !listing.cityName().isEmpty()) {
                    int labelY = Math.round(getPositionY() + CARD_SIZE);
                    String cityText = Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.source", listing.cityName()).getString();
                    var font = Minecraft.getInstance().font;
                    int maxTextW = CARD_SIZE - 8;
                    int textW = font.width(cityText);
                    if (textW > maxTextW) {
                        while (cityText.length() > 2 && font.width(cityText + "…") > maxTextW) {
                            cityText = cityText.substring(0, cityText.length() - 1);
                        }
                        cityText = cityText + "…";
                    }
                    ctx.graphics.drawString(font, cityText,
                            Math.round(getPositionX() + 4), labelY, CITY_LABEL_COLOR, false);
                }
            }
        };

        card.layout(layout -> {
            layout.width(CARD_SIZE);
            layout.height(cardH);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.paddingAll(4);
        }).style(style -> style.backgroundTexture(
                new GuiTextureGroup(new ColorRectTexture(CARD_BG), new ColorBorderTexture(1, BORDER)))
        );

        Label name = new Label();
        name.setText(stack.isEmpty() ? Component.literal(listing.itemId()) : stack.getHoverName());
        name.setOverflowVisible(false);
        name.layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
        });
        name.textStyle(style -> style
                .textColor(TEXT_COLOR).textShadow(false).textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        card.addChild(name);

        UIElement iconSpace = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(ICON_SIZE + 2);
        });
        card.addChild(iconSpace);

        Label countLabel = new Label();
        countLabel.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.count", listing.count()));
        countLabel.setOverflowVisible(false);
        countLabel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(OWNED_H);
        });
        countLabel.textStyle(style -> style.textColor(OWNED_COLOR).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        card.addChild(countLabel);

        Label priceLabel = new Label();
        priceLabel.setText(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.price", listing.price()));
        priceLabel.setOverflowVisible(false);
        priceLabel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(PRICE_H);
        });
        priceLabel.textStyle(style -> style.textColor(SELL_PRICE).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        card.addChild(priceLabel);

        UIElement spacer = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(4);
        });
        card.addChild(spacer);

        if (canOperate) {
            UIElement btnRow = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(BTN_H);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(2);
            });

            if (isSellMode) {
                btnRow.addChild(smallButton(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.cancel"), CANCEL_BTN,
                        () -> sendFreeMarketCancel(listing.id())));
                btnRow.addChild(smallButton(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.modify"), MODIFY_BTN,
                        () -> openModifyPopup(listing)));
            } else {
                btnRow.addChild(smallButton(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.buy"), BUY_BTN,
                        () -> sendFreeMarketBuy(listing.id())));
            }

            card.addChild(btnRow);
        }
        return card;
    }

    private static Button makeSellBuyButton(Component text, int color, Runnable leftAction, Runnable rightAction) {
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> leftAction.run());
        button.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                rightAction.run();
            }
        });
        button.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(0xFFFFFFFF).textShadow(false));
        button.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(1, BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BTN_HOVER_DARK), new ColorBorderTexture(1, BORDER)))
        );
        button.layout(layout -> { layout.flex(1); layout.height(BTN_H); });
        return button;
    }

    private static void openBatchPopup(String cityId, String itemId, boolean isBuy, int unitCount) {
        batchItemId = itemId;
        batchCityId = cityId != null ? cityId : "";
        batchIsBuy = isBuy;
        batchUnitCount = unitCount;
        batchQuantityText = "1";
        batchActiveField = 1;
        batchCursorTick = 0;
        batchAvailableCount = availableCounts.getOrDefault(itemId, 0);
        batchPopupVisible = true;
    }

    private static Button smallButton(Component text, int color, Runnable action) {
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> action.run());
        button.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(0xFFFFFFFF).textShadow(false));
        button.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(1, BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(BTN_HOVER_DARK), new ColorBorderTexture(1, BORDER)))
        );
        button.layout(layout -> { layout.flex(1); layout.height(BTN_H); });
        return button;
    }

    private static void sendTransaction(String cityId, String itemId, boolean isBuy, int amount) {
        if (boxPos == null) return;
        PacketDistributor.sendToServer(new ForeignTradeTransactionPacket(boxPos, cityId, itemId, isBuy, Math.max(1, amount)));
    }

    private static void sendFreeMarketBuy(long listingId) {
        if (boxPos == null) return;
        PacketDistributor.sendToServer(new FreeMarketBuyPacket(boxPos, listingId));
    }

    private static void sendFreeMarketCancel(long listingId) {
        if (boxPos == null) return;
        PacketDistributor.sendToServer(new FreeMarketCancelPacket(boxPos, listingId));
    }

    private static void openModifyPopup(FreeMarketRepository.FreeMarketListing listing) {
        modifyPopupVisible = true;
        modifyListingId = listing.id();
        modifyItemId = listing.itemId();
        modifyItemNbt = listing.itemNbt();
        modifyCountText = String.valueOf(listing.count());
        modifyPriceText = String.valueOf(listing.price());
        modifyActiveField = 1;
        modifyCursorTick = 0;
    }

    private static void goBack() {
        if (boxPos != null) {
            PacketDistributor.sendToServer(new com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenRequestPacket(boxPos));
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    private static boolean onCategoryClicked(double mouseX, double mouseY) {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int barX = sw - CATEGORY_WIDTH;
        int barY = gridStartY;
        int barH = viewportHeight;

        if (mouseX < barX || mouseX >= barX + CATEGORY_WIDTH || mouseY < barY || mouseY >= barY + barH) {
            return false;
        }

        int relY = (int) (mouseY - barY) + categoryScrollOffset;
        int idx = relY / CATEGORY_ITEM_H;
        if (idx >= 0 && idx < CATEGORIES.length) {
            selectedCategoryIndex = idx;
            refreshByCategory();
            return true;
        }
        return true;
    }

    private static boolean onCategoryScrolled(double delta) {
        double mouseX = getMouseX();
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int barX = sw - CATEGORY_WIDTH;
        if (mouseX >= barX) {
            int totalH = CATEGORIES.length * CATEGORY_ITEM_H;
            int maxCatScroll = Math.max(0, totalH - viewportHeight);
            if (maxCatScroll <= 0) return false;
            categoryScrollOffset -= (int) Math.signum(delta) * CATEGORY_ITEM_H;
            categoryScrollOffset = Math.max(0, Math.min(categoryScrollOffset, maxCatScroll));
            return true;
        }
        return false;
    }

    private static double getMouseX() {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Minecraft.getInstance().mouseHandler.xpos() * sw / Minecraft.getInstance().getWindow().getScreenWidth();
    }

    private static double getMouseY() {
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Minecraft.getInstance().mouseHandler.ypos() * sh / Minecraft.getInstance().getWindow().getScreenHeight();
    }

    static boolean onMouseScrolled(double delta) {
        if (modifyPopupVisible) return true;
        if (warehousePanelVisible && isMouseInWarehousePanel(getMouseX(), getMouseY())) {
            double mouseY = getMouseY();
            int gridTopY = warehousePanelY + WP_TITLE_H;
            int gridBottomY = gridTopY + WP_WAREHOUSE_GRID_H;
            if (mouseY >= gridTopY && mouseY < gridBottomY) {
                int totalRows = (warehouseItems.size() + WP_COLS - 1) / WP_COLS;
                int visibleItemRows = WP_WAREHOUSE_GRID_H / WP_SLOT_SIZE;
                int maxScroll = Math.max(0, (totalRows - visibleItemRows) * WP_SLOT_SIZE);
                warehouseScrollOffset -= (int) Math.signum(delta) * WP_SLOT_SIZE;
                warehouseScrollOffset = Math.max(0, Math.min(warehouseScrollOffset, maxScroll));
            }
            return true;
        }

        if (onCategoryScrolled(delta)) return true;

        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0) return false;
        int step = getEffectiveCardH() + getEffectiveGap();
        scrollOffset -= (int) Math.signum(delta) * step;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        addVisibleCards();
        return true;
    }

    static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (modifyPopupVisible && button == 0) {
            return onModifyPopupClicked(mouseX, mouseY);
        }
        if (warehousePanelVisible && button == 0) {
            if (onWarehousePanelClicked(mouseX, mouseY)) return true;
        }

        if (button == 0 && onCategoryClicked(mouseX, mouseY)) return true;

        if (button == 1 && isFreeSellCategory()) {
            int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int scrollbarSpace = SCROLLBAR_WIDTH + SCROLLBAR_MARGIN * 2;
            if (mouseX >= TOP_GAP && mouseX < sw - CATEGORY_WIDTH - scrollbarSpace
                    && mouseY >= gridStartY && mouseY < gridStartY + viewportHeight) {
                warehousePanelVisible = true;
                warehousePricingMode = false;
                warehouseSelectedItem = null;
                warehouseSelectedNbt = "";
                warehouseCountText = "1";
                warehousePriceText = "10";
                warehouseActiveField = 0;
                warehouseScrollOffset = 0;
                if (boxPos != null) {
                    PacketDistributor.sendToServer(new FreeMarketWarehouseRequestPacket(boxPos));
                }
                return true;
            }
        }

        if (button != 0) return false;
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0) return false;

        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int trackX = sw - CATEGORY_WIDTH - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int trackY = gridStartY;
        int trackH = viewportHeight;
        int thumbH = Math.max(20, (int) ((float) viewportHeight / contentHeight * trackH));
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = trackY + (int) (scrollRatio * (trackH - thumbH));

        if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH
                && mouseY >= thumbY && mouseY <= thumbY + thumbH) {
            draggingScrollbar = true;
            return true;
        }
        if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH
                && mouseY >= trackY && mouseY <= trackY + trackH) {
            float ratio = (float) (mouseY - trackY) / trackH;
            scrollOffset = Math.max(0, Math.min((int) (ratio * maxScroll), maxScroll));
            draggingScrollbar = true;
            addVisibleCards();
            return true;
        }
        return false;
    }

    static boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingWarehousePanel) {
            warehousePanelX = panelStartX + (int) mouseX - dragStartX;
            warehousePanelY = panelStartY + (int) mouseY - dragStartY;
            return true;
        }
        if (!draggingScrollbar) return false;
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0) return false;

        int trackY = gridStartY;
        int trackH = viewportHeight;
        float ratio = (float) Math.max(0, Math.min(1.0, (mouseY - trackY) / trackH));
        scrollOffset = Math.max(0, Math.min((int) (ratio * maxScroll), maxScroll));
        addVisibleCards();
        return true;
    }

    static boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (draggingWarehousePanel) {
            draggingWarehousePanel = false;
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    public static void updateWarehouseData(List<FreeMarketWarehouseRequestPacket.WarehouseItem> items) {
        warehouseItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    private static boolean isMouseInWarehousePanel(double mouseX, double mouseY) {
        return mouseX >= warehousePanelX && mouseX < warehousePanelX + WP_WIDTH
                && mouseY >= warehousePanelY && mouseY < warehousePanelY + WP_HEIGHT;
    }

    private static boolean onWarehousePanelClicked(double mouseX, double mouseY) {
        int px = warehousePanelX;
        int py = warehousePanelY;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (!isMouseInWarehousePanel(mouseX, mouseY)) {
            warehousePanelVisible = false;
            warehouseActiveField = 0;
            return false;
        }

        int closeX = px + WP_WIDTH - WP_CLOSE_SIZE - 4;
        int closeY = py + 4;
        if (mx >= closeX && mx < closeX + WP_CLOSE_SIZE && my >= closeY && my < closeY + WP_CLOSE_SIZE) {
            warehousePanelVisible = false;
            warehousePricingMode = false;
            warehouseSelectedItem = null;
            warehouseSelectedNbt = "";
            warehouseActiveField = 0;
            return true;
        }

        if (my >= py && my < py + WP_TITLE_H && mx < closeX) {
            draggingWarehousePanel = true;
            dragStartX = mx;
            dragStartY = my;
            panelStartX = warehousePanelX;
            panelStartY = warehousePanelY;
            return true;
        }

        if (warehousePricingMode) {
            int baseX = px + 10;
            int baseY = py + WP_TITLE_H + 10;
            int inputBaseY = baseY + 30;

            int countInputX = baseX + 50;
            int countInputY = inputBaseY;
            if (mx >= countInputX && mx < countInputX + WP_INPUT_W && my >= countInputY && my < countInputY + WP_INPUT_H) {
                warehouseActiveField = 1;
                return true;
            }

            int priceInputX = baseX + 50;
            int priceInputY = inputBaseY + 24;
            if (mx >= priceInputX && mx < priceInputX + WP_INPUT_W && my >= priceInputY && my < priceInputY + WP_INPUT_H) {
                warehouseActiveField = 2;
                return true;
            }

            int confirmBtnX = baseX;
            int confirmBtnY = inputBaseY + 54;
            if (mx >= confirmBtnX && mx < confirmBtnX + WP_BTN_W && my >= confirmBtnY && my < confirmBtnY + WP_BTN_H) {
                confirmWarehouseListing();
                return true;
            }

            int cancelBtnX = baseX + WP_BTN_W + 10;
            int cancelBtnY = inputBaseY + 54;
            if (mx >= cancelBtnX && mx < cancelBtnX + WP_BTN_W && my >= cancelBtnY && my < cancelBtnY + WP_BTN_H) {
                warehousePricingMode = false;
                warehouseSelectedItem = null;
                warehouseSelectedNbt = "";
                warehouseActiveField = 0;
                return true;
            }

            warehouseActiveField = 0;
            return true;
        } else {
            int gridX = px + 4;
            int gridY = py + WP_TITLE_H;
            int relX = mx - gridX;
            int relY = my - gridY + warehouseScrollOffset;
            if (relX >= 0 && relX < WP_COLS * WP_SLOT_SIZE && relY >= 0) {
                int col = relX / WP_SLOT_SIZE;
                int row = relY / WP_SLOT_SIZE;
                int idx = row * WP_COLS + col;
                if (idx >= 0 && idx < warehouseItems.size()) {
                    warehouseSelectedItem = warehouseItems.get(idx).itemId();
                    warehouseSelectedNbt = warehouseItems.get(idx).itemNbt() != null ? warehouseItems.get(idx).itemNbt() : "";
                    warehousePricingMode = true;
                    warehouseCountText = "1";
                    warehousePriceText = "10";
                    warehouseActiveField = 1;
                    return true;
                }
            }

            int sepY = py + WP_TITLE_H + WP_WAREHOUSE_GRID_H;
            int invStartY = sepY + WP_SEP_H + WP_INV_TITLE_H;
            int invGridX = px + 4;
            int invRelX = mx - invGridX;
            int invRelY = my - invStartY;
            if (invRelX >= 0 && invRelX < WP_COLS * WP_SLOT_SIZE && invRelY >= 0 && invRelY < WP_INV_ROWS * WP_SLOT_SIZE) {
                int col = invRelX / WP_SLOT_SIZE;
                int row = invRelY / WP_SLOT_SIZE;
                int actualIdx = row < 3 ? (row + 1) * 9 + col : col;
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    ItemStack stack = mc.player.getInventory().getItem(actualIdx);
                    if (!stack.isEmpty()) {
                        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        warehouseSelectedItem = rl.toString();
                        warehousePricingMode = true;
                        warehouseCountText = "1";
                        warehousePriceText = "10";
                        warehouseActiveField = 1;
                        return true;
                    }
                }
            }

            return true;
        }
    }

    private static void confirmWarehouseListing() {
        if (warehouseSelectedItem == null || boxPos == null) return;
        int count = parseIntSafe(warehouseCountText, 1);
        int price = parseIntSafe(warehousePriceText, 10);
        if (count <= 0 || price <= 0) return;
        PacketDistributor.sendToServer(new FreeMarketListPacket(boxPos, warehouseSelectedItem, count, price, warehouseSelectedNbt));
        warehousePanelVisible = false;
        warehousePricingMode = false;
        warehouseSelectedItem = null;
        warehouseSelectedNbt = "";
        warehouseActiveField = 0;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    static void renderWarehousePanel(GuiGraphics gg, int mouseX, int mouseY) {
        if (!warehousePanelVisible) return;
        warehouseCursorTick++;

        int px = warehousePanelX;
        int py = warehousePanelY;
        var font = Minecraft.getInstance().font;

        gg.fill(px, py, px + WP_WIDTH, py + WP_HEIGHT, 0xDD303030);

        gg.fill(px, py, px + WP_WIDTH, py + WP_TITLE_H, 0xFF444444);
        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.warehouse").getString(), px + 6, py + 6, 0xFFFFFFFF, false);

        int closeX = px + WP_WIDTH - WP_CLOSE_SIZE - 4;
        int closeY = py + 4;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + WP_CLOSE_SIZE
                && mouseY >= closeY && mouseY < closeY + WP_CLOSE_SIZE;
        gg.fill(closeX, closeY, closeX + WP_CLOSE_SIZE, closeY + WP_CLOSE_SIZE,
                closeHovered ? 0xFF666666 : 0xFF555555);
        gg.drawCenteredString(font, "×", closeX + WP_CLOSE_SIZE / 2, closeY + 2, 0xFFFFFFFF);

        if (warehousePricingMode) {
            renderWarehousePricingMode(gg, px, py, mouseX, mouseY);
        } else {
            renderWarehouseItemGrid(gg, px, py, mouseX, mouseY);

            int sepY = py + WP_TITLE_H + WP_WAREHOUSE_GRID_H;
            gg.fill(px + 2, sepY + 1, px + WP_WIDTH - 2, sepY + 2, 0xFF666666);

            int invStartY = sepY + WP_SEP_H;
            renderPlayerInventory(gg, px, py, invStartY, mouseX, mouseY);
        }

        gg.renderOutline(px, py, WP_WIDTH, WP_HEIGHT, 0xFF000000);
    }

    private static void renderWarehouseItemGrid(GuiGraphics gg, int px, int py, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;

        if (warehouseItems.isEmpty()) {
            gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.warehouse_empty").getString(), px + 10, py + WP_TITLE_H + 10, 0xFFAAAAAA, false);
            return;
        }

        int gridX = px + 4;
        int gridY = py + WP_TITLE_H;
        int gridH = WP_WAREHOUSE_GRID_H;

        gg.enableScissor(gridX, gridY, gridX + WP_COLS * WP_SLOT_SIZE, gridY + gridH);

        int startRow = warehouseScrollOffset / WP_SLOT_SIZE;
        int yOffset = -(warehouseScrollOffset % WP_SLOT_SIZE);

        for (int i = startRow; i * WP_COLS < warehouseItems.size(); i++) {
            for (int c = 0; c < WP_COLS; c++) {
                int idx = i * WP_COLS + c;
                if (idx >= warehouseItems.size()) break;
                int slotX = gridX + c * WP_SLOT_SIZE;
                int slotY = gridY + yOffset + (i - startRow) * WP_SLOT_SIZE;
                if (slotY + WP_SLOT_SIZE < gridY || slotY >= gridY + gridH) continue;

                var item = warehouseItems.get(idx);
                ItemStack stack = ItemStack.EMPTY;
                if (item.itemNbt() != null && !item.itemNbt().isEmpty()) {
                    try {
                        var tag = net.minecraft.nbt.TagParser.parseTag(item.itemNbt());
                        stack = ItemStack.parseOptional(
                                Minecraft.getInstance().level.registryAccess(),
                                (net.minecraft.nbt.CompoundTag) tag);
                    } catch (Exception ignored) {}
                }
                if (stack.isEmpty()) {
                    ResourceLocation rl = ResourceLocation.tryParse(item.itemId());
                    Item mcItem = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
                    stack = mcItem != null ? new ItemStack(mcItem) : ItemStack.EMPTY;
                }

                boolean hovered = mouseX >= slotX && mouseX < slotX + WP_SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + WP_SLOT_SIZE;
                gg.fill(slotX, slotY, slotX + WP_SLOT_SIZE, slotY + WP_SLOT_SIZE,
                        hovered ? 0xFF555555 : 0xFF404040);

                if (!stack.isEmpty()) {
                    gg.renderItem(stack, slotX + 2, slotY + 2);
                    if (item.count() > 1) {
                        String countStr = String.valueOf(item.count());
                        var pose = gg.pose();
                        pose.pushPose();
                        pose.translate(0, 0, 200);
                        gg.drawString(font, countStr, slotX + WP_SLOT_SIZE - font.width(countStr) - 1,
                                slotY + WP_SLOT_SIZE - 9, 0xFFFFFFFF, false);
                        pose.popPose();
                    }
                }

                gg.renderOutline(slotX, slotY, WP_SLOT_SIZE, WP_SLOT_SIZE, 0xFF666666);
            }
        }
        gg.disableScissor();
    }

    private static void renderPlayerInventory(GuiGraphics gg, int px, int py, int invStartY, int mouseX, int mouseY) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var font = mc.font;
        var inv = mc.player.getInventory();

        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.player_inventory").getString(), px + 4, invStartY, 0xFFAAAAAA, false);
        invStartY += WP_INV_TITLE_H;

        int gridX = px + 4;
        for (int row = 0; row < WP_INV_ROWS; row++) {
            for (int col = 0; col < WP_COLS; col++) {
                int actualIdx = row < 3 ? (row + 1) * 9 + col : col;
                ItemStack stack = inv.getItem(actualIdx);

                int slotX = gridX + col * WP_SLOT_SIZE;
                int slotY = invStartY + row * WP_SLOT_SIZE;

                boolean hovered = mouseX >= slotX && mouseX < slotX + WP_SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + WP_SLOT_SIZE;
                gg.fill(slotX, slotY, slotX + WP_SLOT_SIZE, slotY + WP_SLOT_SIZE,
                        hovered ? 0xFF555555 : 0xFF404040);

                if (!stack.isEmpty()) {
                    gg.renderItem(stack, slotX + 2, slotY + 2);
                    if (stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        var pose = gg.pose();
                        pose.pushPose();
                        pose.translate(0, 0, 200);
                        gg.drawString(font, countStr, slotX + WP_SLOT_SIZE - font.width(countStr) - 1,
                                slotY + WP_SLOT_SIZE - 9, 0xFFFFFFFF);
                        pose.popPose();
                    }
                }

                gg.renderOutline(slotX, slotY, WP_SLOT_SIZE, WP_SLOT_SIZE, 0xFF666666);
            }
        }
    }

    private static void renderWarehousePricingMode(GuiGraphics gg, int px, int py, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int baseX = px + 10;
        int baseY = py + WP_TITLE_H + 10;

        ItemStack stack = ItemStack.EMPTY;
        if (warehouseSelectedNbt != null && !warehouseSelectedNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(warehouseSelectedNbt);
                stack = ItemStack.parseOptional(
                        Minecraft.getInstance().level.registryAccess(),
                        (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            ResourceLocation rl = warehouseSelectedItem != null ? ResourceLocation.tryParse(warehouseSelectedItem) : null;
            Item mcItem = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            stack = mcItem != null ? new ItemStack(mcItem) : ItemStack.EMPTY;
        }

        if (!stack.isEmpty()) {
            gg.renderItem(stack, baseX, baseY);
            gg.drawString(font, stack.getHoverName().getString(), baseX + 22, baseY + 4, 0xFFFFFFFF, false);
        } else {
            gg.drawString(font, warehouseSelectedItem != null ? warehouseSelectedItem : "", baseX, baseY + 4, 0xFFFFFFFF, false);
        }

        int inputBaseY = baseY + 30;

        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.count_label").getString(), baseX, inputBaseY + 4, 0xFFCCCCCC, false);
        int countInputX = baseX + 50;
        int countInputY = inputBaseY;
        renderInputField(gg, countInputX, countInputY, warehouseCountText, warehouseActiveField == 1);

        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.price_label").getString(), baseX, inputBaseY + 28, 0xFFCCCCCC, false);
        int priceInputX = baseX + 50;
        int priceInputY = inputBaseY + 24;
        renderInputField(gg, priceInputX, priceInputY, warehousePriceText, warehouseActiveField == 2);

        int confirmBtnX = baseX;
        int confirmBtnY = inputBaseY + 54;
        boolean confirmHovered = mouseX >= confirmBtnX && mouseX < confirmBtnX + WP_BTN_W
                && mouseY >= confirmBtnY && mouseY < confirmBtnY + WP_BTN_H;
        gg.fill(confirmBtnX, confirmBtnY, confirmBtnX + WP_BTN_W, confirmBtnY + WP_BTN_H,
                confirmHovered ? 0xFF444444 : 0xFF5B9BD5);
        gg.drawCenteredString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.confirm_list").getString(), confirmBtnX + WP_BTN_W / 2, confirmBtnY + 5, 0xFFFFFFFF);

        int cancelBtnX = baseX + WP_BTN_W + 10;
        int cancelBtnY = inputBaseY + 54;
        boolean cancelHovered = mouseX >= cancelBtnX && mouseX < cancelBtnX + WP_BTN_W
                && mouseY >= cancelBtnY && mouseY < cancelBtnY + WP_BTN_H;
        gg.fill(cancelBtnX, cancelBtnY, cancelBtnX + WP_BTN_W, cancelBtnY + WP_BTN_H,
                cancelHovered ? 0xFF444444 : 0xFFD9534F);
        gg.drawCenteredString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.cancel").getString(), cancelBtnX + WP_BTN_W / 2, cancelBtnY + 5, 0xFFFFFFFF);
    }

    private static void renderInputField(GuiGraphics gg, int x, int y, String text, boolean focused) {
        var font = Minecraft.getInstance().font;
        gg.fill(x, y, x + WP_INPUT_W, y + WP_INPUT_H, 0xFF1A1A1A);
        gg.renderOutline(x, y, WP_INPUT_W, WP_INPUT_H, focused ? 0xFFAAAAAA : 0xFF666666);
        gg.drawString(font, text, x + 4, y + 4, 0xFFFFFFFF, false);
        if (focused && (warehouseCursorTick / 20) % 2 == 0) {
            int cursorX = x + 4 + font.width(text);
            gg.fill(cursorX, y + 3, cursorX + 1, y + WP_INPUT_H - 3, 0xFFFFFFFF);
        }
    }

    static boolean onWarehouseCharTyped(char codePoint, int modifiers) {
        if (!warehousePanelVisible || !warehousePricingMode || warehouseActiveField == 0) return false;
        if (Character.isDigit(codePoint)) {
            if (warehouseActiveField == 1 && warehouseCountText.length() < 6) {
                warehouseCountText += codePoint;
            } else if (warehouseActiveField == 2 && warehousePriceText.length() < 6) {
                warehousePriceText += codePoint;
            }
            return true;
        }
        return false;
    }

    static boolean onWarehouseKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!warehousePanelVisible || !warehousePricingMode || warehouseActiveField == 0) return false;
        String target = warehouseActiveField == 1 ? warehouseCountText : warehousePriceText;
        if (keyCode == 259) {
            if (!target.isEmpty()) {
                target = target.substring(0, target.length() - 1);
            }
        } else if (keyCode == 258) {
            warehouseActiveField = warehouseActiveField == 1 ? 2 : 1;
            return true;
        } else if (keyCode == 257 || keyCode == 335) {
            confirmWarehouseListing();
            return true;
        } else {
            return false;
        }
        if (warehouseActiveField == 1) warehouseCountText = target;
        else warehousePriceText = target;
        return true;
    }

    static void renderModifyPopup(GuiGraphics gg, int mouseX, int mouseY) {
        if (!modifyPopupVisible) return;
        modifyCursorTick++;
        var mc = Minecraft.getInstance();
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        gg.fill(0, 0, sw, sh, 0x80000000);
        int px = (sw - MP_W) / 2;
        int py = (sh - MP_H) / 2;
        gg.fill(px, py, px + MP_W, py + MP_H, 0xFFE8E8E8);
        gg.renderOutline(px, py, MP_W, MP_H, 0xFF000000);
        ItemStack stack = ItemStack.EMPTY;
        if (modifyItemNbt != null && !modifyItemNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(modifyItemNbt);
                stack = ItemStack.parseOptional(mc.level.registryAccess(), (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(modifyItemId);
            Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        if (!stack.isEmpty()) {
            gg.renderItem(stack, px + 20, py + 12);
            gg.drawString(font, stack.getHoverName().getString(), px + 42, py + 16, 0xFF1A1A1A, false);
        } else {
            gg.drawString(font, modifyItemId != null ? modifyItemId : "", px + 20, py + 16, 0xFF1A1A1A, false);
        }
        int baseX = px + 20;
        int baseY = py + 40;
        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.count_label").getString(), baseX, baseY + 4, 0xFF1A1A1A, false);
        renderModifyInputField(gg, baseX + 50, baseY, modifyCountText, modifyActiveField == 1);
        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.price_label").getString(), baseX, baseY + 28, 0xFF1A1A1A, false);
        renderModifyInputField(gg, baseX + 50, baseY + 28, modifyPriceText, modifyActiveField == 2);
        int confirmX = baseX;
        int confirmY = baseY + 56;
        boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + MP_BTN_W
                && mouseY >= confirmY && mouseY < confirmY + MP_BTN_H;
        gg.fill(confirmX, confirmY, confirmX + MP_BTN_W, confirmY + MP_BTN_H,
                confirmHovered ? 0xFF444444 : 0xFF5B9BD5);
        gg.drawCenteredString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.confirm").getString(), confirmX + MP_BTN_W / 2, confirmY + 5, 0xFFFFFFFF);
        int cancelX = baseX + MP_BTN_W + 10;
        int cancelY = baseY + 56;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + MP_BTN_W
                && mouseY >= cancelY && mouseY < cancelY + MP_BTN_H;
        gg.fill(cancelX, cancelY, cancelX + MP_BTN_W, cancelY + MP_BTN_H,
                cancelHovered ? 0xFF444444 : 0xFFD9534F);
        gg.drawCenteredString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.cancel").getString(), cancelX + MP_BTN_W / 2, cancelY + 5, 0xFFFFFFFF);
    }

    private static void renderModifyInputField(GuiGraphics gg, int x, int y, String text, boolean focused) {
        var font = Minecraft.getInstance().font;
        gg.fill(x, y, x + MP_INPUT_W, y + MP_INPUT_H, 0xFFFFFFFF);
        gg.renderOutline(x, y, MP_INPUT_W, MP_INPUT_H, focused ? 0xFF000000 : 0xFF999999);
        gg.drawString(font, text, x + 4, y + 4, 0xFF1A1A1A, false);
        if (focused && (modifyCursorTick / 20) % 2 == 0) {
            int cursorX = x + 4 + font.width(text);
            gg.fill(cursorX, y + 3, cursorX + 1, y + MP_INPUT_H - 3, 0xFF000000);
        }
    }

    private static boolean onModifyPopupClicked(double mouseX, double mouseY) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int px = (sw - MP_W) / 2;
        int py = (sh - MP_H) / 2;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (mx < px || mx >= px + MP_W || my < py || my >= py + MP_H) {
            modifyPopupVisible = false;
            modifyActiveField = 0;
            return true;
        }
        int baseX = px + 20;
        int baseY = py + 40;
        if (mx >= baseX + 50 && mx < baseX + 50 + MP_INPUT_W && my >= baseY && my < baseY + MP_INPUT_H) {
            modifyActiveField = 1;
            return true;
        }
        if (mx >= baseX + 50 && mx < baseX + 50 + MP_INPUT_W && my >= baseY + 28 && my < baseY + 28 + MP_INPUT_H) {
            modifyActiveField = 2;
            return true;
        }
        int confirmX = baseX;
        int confirmY = baseY + 56;
        if (mx >= confirmX && mx < confirmX + MP_BTN_W && my >= confirmY && my < confirmY + MP_BTN_H) {
            confirmModifyListing();
            return true;
        }
        int cancelX = baseX + MP_BTN_W + 10;
        int cancelY = baseY + 56;
        if (mx >= cancelX && mx < cancelX + MP_BTN_W && my >= cancelY && my < cancelY + MP_BTN_H) {
            modifyPopupVisible = false;
            modifyActiveField = 0;
            return true;
        }
        return true;
    }

    private static void confirmModifyListing() {
        int count = parseIntSafe(modifyCountText, 1);
        int price = parseIntSafe(modifyPriceText, 10);
        if (count <= 0 || price <= 0) return;
        if (boxPos == null) return;
        PacketDistributor.sendToServer(new FreeMarketModifyPacket(boxPos, modifyListingId, count, price));
        modifyPopupVisible = false;
        modifyActiveField = 0;
    }

    static boolean onModifyPopupCharTyped(char codePoint, int modifiers) {
        if (!modifyPopupVisible || modifyActiveField == 0) return false;
        if (Character.isDigit(codePoint)) {
            if (modifyActiveField == 1 && modifyCountText.length() < 6) {
                modifyCountText += codePoint;
            } else if (modifyActiveField == 2 && modifyPriceText.length() < 6) {
                modifyPriceText += codePoint;
            }
            return true;
        }
        return false;
    }

    static boolean onModifyPopupKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!modifyPopupVisible) return false;
        if (keyCode == 256) {
            modifyPopupVisible = false;
            modifyActiveField = 0;
            return true;
        }
        if (modifyActiveField == 0) return false;
        String target = modifyActiveField == 1 ? modifyCountText : modifyPriceText;
        if (keyCode == 259) {
            if (!target.isEmpty()) {
                target = target.substring(0, target.length() - 1);
            }
        } else if (keyCode == 258) {
            modifyActiveField = modifyActiveField == 1 ? 2 : 1;
            return true;
        } else if (keyCode == 257 || keyCode == 335) {
            confirmModifyListing();
            return true;
        } else {
            return false;
        }
        if (modifyActiveField == 1) modifyCountText = target;
        else modifyPriceText = target;
        return true;
    }


    static void renderBatchPopup(GuiGraphics gg, int mouseX, int mouseY) {
        if (!batchPopupVisible || batchItemId == null) return;
        var pose = gg.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        batchCursorTick++;
        var mc = Minecraft.getInstance();
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int px = (sw - BP_W) / 2;
        int py = (sh - BP_H) / 2;

        gg.fill(0, 0, sw, sh, 0x80000000);
        gg.fill(px, py, px + BP_W, py + BP_H, 0xFFE8E8E8);
        gg.renderOutline(px, py, BP_W, BP_H, 0xFF000000);

        int baseX = px + 10;
        int baseY = py + 10;

        ResourceLocation rl = ResourceLocation.tryParse(batchItemId);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            gg.renderItem(stack, baseX, baseY);
            gg.drawString(font, stack.getHoverName().getString(), baseX + 20, baseY + 4, 0xFF1A1A1A, false);
        } else {
            gg.drawString(font, batchItemId, baseX, baseY + 4, 0xFF1A1A1A, false);
        }

        String actionText = batchIsBuy
                ? Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch.buy").getString()
                : Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch.sell").getString();
        gg.drawString(font, actionText, baseX, baseY + 22, 0xFF1A1A1A, false);

        String unitInfo = Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch.unit",
                batchUnitCount, batchIsBuy ? findMarketBuyPrice(batchCityId, batchItemId) : findMarketSellPrice(batchCityId, batchItemId)).getString();
        gg.drawString(font, unitInfo, baseX, baseY + 34, 0xFF555555, false);

        if (!batchIsBuy) {
            String availText = Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch.available", batchAvailableCount).getString();
            gg.drawString(font, availText, baseX, baseY + 46, batchAvailableCount <= 0 ? 0xFFD9534F : 0xFF555555, false);
        }

        int inputY = batchIsBuy ? baseY + 48 : baseY + 60;
        gg.drawString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch.quantity").getString(), baseX, inputY + 4, 0xFF1A1A1A, false);
        int inputX = baseX + 56;
        gg.fill(inputX, inputY, inputX + BP_INPUT_W, inputY + BP_INPUT_H, 0xFFFFFFFF);
        gg.renderOutline(inputX, inputY, BP_INPUT_W, BP_INPUT_H, batchActiveField == 1 ? 0xFF000000 : 0xFF999999);
        gg.drawString(font, batchQuantityText, inputX + 4, inputY + 4, 0xFF1A1A1A, false);
        if (batchActiveField == 1 && (batchCursorTick / 20) % 2 == 0) {
            int cursorX = inputX + 4 + font.width(batchQuantityText);
            gg.fill(cursorX, inputY + 3, cursorX + 1, inputY + BP_INPUT_H - 3, 0xFF000000);
        }

        int btnY = inputY + 26;
        boolean confirmHov = mouseX >= baseX && mouseX < baseX + BP_BTN_W && mouseY >= btnY && mouseY < btnY + BP_BTN_H;
        gg.fill(baseX, btnY, baseX + BP_BTN_W, btnY + BP_BTN_H, confirmHov ? 0xFF444444 : 0xFF5B9BD5);
        gg.drawCenteredString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.confirm").getString(),
                baseX + BP_BTN_W / 2, btnY + 5, 0xFFFFFFFF);

        int cancelX = baseX + BP_BTN_W + 8;
        boolean cancelHov = mouseX >= cancelX && mouseX < cancelX + BP_BTN_W && mouseY >= btnY && mouseY < btnY + BP_BTN_H;
        gg.fill(cancelX, btnY, cancelX + BP_BTN_W, btnY + BP_BTN_H, cancelHov ? 0xFF444444 : 0xFFD9534F);
        gg.drawCenteredString(font, Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.cancel").getString(),
                cancelX + BP_BTN_W / 2, btnY + 5, 0xFFFFFFFF);

        if (!batchIsBuy && !batchQuantityText.isEmpty()) {
            int qty = parseIntSafe(batchQuantityText, 0);
            if (qty * batchUnitCount > batchAvailableCount && batchAvailableCount > 0) {
                String warn = Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.batch.exceed", batchAvailableCount / batchUnitCount).getString();
                gg.drawString(font, warn, baseX, btnY + 24, 0xFFD9534F, false);
            }
        }
        pose.popPose();
    }

    static boolean onBatchPopupClicked(double mouseX, double mouseY, int button) {
        if (!batchPopupVisible) return false;
        if (button != 0) return true;
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int px = (sw - BP_W) / 2;
        int py = (sh - BP_H) / 2;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (mx < px || mx >= px + BP_W || my < py || my >= py + BP_H) {
            batchPopupVisible = false;
            return true;
        }

        int baseX = px + 10;
        int inputY = batchIsBuy ? py + 58 : py + 70;
        int inputX = baseX + 56;

        if (mx >= inputX && mx < inputX + BP_INPUT_W && my >= inputY && my < inputY + BP_INPUT_H) {
            batchActiveField = 1;
            return true;
        }

        int btnY = inputY + 26;
        if (mx >= baseX && mx < baseX + BP_BTN_W && my >= btnY && my < btnY + BP_BTN_H) {
            confirmBatchTransaction();
            return true;
        }

        int cancelX = baseX + BP_BTN_W + 8;
        if (mx >= cancelX && mx < cancelX + BP_BTN_W && my >= btnY && my < btnY + BP_BTN_H) {
            batchPopupVisible = false;
            return true;
        }

        batchActiveField = 0;
        return true;
    }

    private static void confirmBatchTransaction() {
        int qty = parseIntSafe(batchQuantityText, 1);
        if (qty <= 0) qty = 1;
        if (qty > 999) qty = 999;
        if (!batchIsBuy && batchAvailableCount > 0 && qty * batchUnitCount > batchAvailableCount) {
            return;
        }
        sendTransaction(batchCityId, batchItemId, batchIsBuy, qty);
        batchPopupVisible = false;
    }

    static boolean onBatchPopupCharTyped(char codePoint, int modifiers) {
        if (!batchPopupVisible || batchActiveField != 1) return false;
        if (Character.isDigit(codePoint) && batchQuantityText.length() < 6) {
            batchQuantityText += codePoint;
            return true;
        }
        return false;
    }

    static boolean onBatchPopupKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!batchPopupVisible) return false;
        if (keyCode == 256) {
            batchPopupVisible = false;
            return true;
        }
        if (batchActiveField != 1) return false;
        if (keyCode == 259) {
            if (!batchQuantityText.isEmpty()) {
                batchQuantityText = batchQuantityText.substring(0, batchQuantityText.length() - 1);
            }
            return true;
        } else if (keyCode == 257 || keyCode == 335) {
            confirmBatchTransaction();
            return true;
        }
        return false;
    }

    private static double findMarketSellPrice(String cityId, String itemId) {
        String villageType = DiplomacyClientCache.getVillageTypeByCityId(cityId);
        for (var e : marketData) {
            if (e.itemId().equals(itemId) && matchesVillage(e, villageType)) return e.sellPrice();
        }
        return 0;
    }

    private static double findMarketBuyPrice(String cityId, String itemId) {
        String villageType = DiplomacyClientCache.getVillageTypeByCityId(cityId);
        for (var e : marketData) {
            if (e.itemId().equals(itemId) && matchesVillage(e, villageType)) return e.buyPrice();
        }
        return 0;
    }

    private static boolean matchesVillage(ForeignTradeMarket.MarketEntry e, String villageType) {
        String vt = villageType != null ? villageType : "";
        String ev = e.villageType() != null ? e.villageType() : "";
        return ev.equals(vt);
    }

    private static String formatPrice(double price) {
        if (price == (long) price) {
            return String.valueOf((long) price);
        }
        return String.format("%.1f", price);
    }

    private static final class ForeignTradeMenuScreen extends ModularUIScreen {
        private ForeignTradeMenuScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
            super.render(gg, mouseX, mouseY, partialTick);
            ForeignTradeMenuScreenOpener.renderWarehousePanel(gg, mouseX, mouseY);
            ForeignTradeMenuScreenOpener.renderModifyPopup(gg, mouseX, mouseY);
            ForeignTradeMenuScreenOpener.renderBatchPopup(gg, mouseX, mouseY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (ForeignTradeMenuScreenOpener.onMouseScrolled(scrollY)) return true;
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (ForeignTradeMenuScreenOpener.onBatchPopupClicked(mouseX, mouseY, button)) return true;
            if (ForeignTradeMenuScreenOpener.onMouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (ForeignTradeMenuScreenOpener.onMouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (ForeignTradeMenuScreenOpener.onMouseReleased(mouseX, mouseY, button)) return true;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (ForeignTradeMenuScreenOpener.onBatchPopupCharTyped(codePoint, modifiers)) return true;
            if (ForeignTradeMenuScreenOpener.onModifyPopupCharTyped(codePoint, modifiers)) return true;
            if (ForeignTradeMenuScreenOpener.onWarehouseCharTyped(codePoint, modifiers)) return true;
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (ForeignTradeMenuScreenOpener.onBatchPopupKeyPressed(keyCode, scanCode, modifiers)) return true;
            if (ForeignTradeMenuScreenOpener.onModifyPopupKeyPressed(keyCode, scanCode, modifiers)) return true;
            if (ForeignTradeMenuScreenOpener.onWarehouseKeyPressed(keyCode, scanCode, modifiers)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
