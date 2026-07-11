package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.client.gui.CitizenHeadRenderer;
import com.xy2407.nsukaddition.client.gui.ScrollablePanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;

/** 城市人口全屏界面，展示市民列表并支持职业/住房筛选和搜索。 */
@OnlyIn(Dist.CLIENT)
public final class PopulationScreen extends Screen {

    private static final int BG_COLOR = 0xFF444444;
    private static final int TITLE_BAR_BG = 0xFF303030;
    private static final int DIVIDER = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE6E6E6;
    private static final int TEXT_MUTED = 0xFFBDBDBD;
    private static final int CARD_BG = 0xFF3A3A3A;
    private static final int CARD_BG_ALT = 0xFF363636;
    private static final int COLOR_EMPLOYED = 0xFF66FF66;
    private static final int COLOR_UNEMPLOYED = 0xFFFF6666;
    private static final int COLOR_HOMED = 0xFFE6B800;
    private static final int TAB_BG = 0xFF333333;
    private static final int TAB_BG_ACTIVE = 0xFF4A4A4A;
    private static final int TAB_BG_HOVER = 0xFF555555;
    private static final int SEARCH_BG = 0xFF2C2C2C;
    private static final int SEARCH_BORDER = 0xFF555555;
    private static final int AVATAR_BORDER = 0xFFFFFFFF;

    private static final int WINDOW_W = 420;
    private static final int WINDOW_H = 340;
    private static final int PADDING = 12;
    private static final int TITLE_H = 24;
    private static final int SEARCH_W = 140;
    private static final int SEARCH_H = 16;
    private static final int TAB_H = 20;
    private static final int TAB_Y = TITLE_H + 28;
    private static final int ITEM_H = 40;
    private static final int ITEM_GAP = 2;
    private static final int AVATAR_SIZE = 28;

    private static final String FILTER_ALL = "all";
    private static final String FILTER_EMPLOYED = "employed";
    private static final String FILTER_UNEMPLOYED = "unemployed";
    private static final String FILTER_HOMED = "homed";
    private static final String FILTER_HOMELESS = "homeless";

    private static final Map<String, String> JOB_NAMES = new HashMap<>();

    static {
        JOB_NAMES.put("UNEMPLOYED", "无业");
        JOB_NAMES.put("FARMER", "农民");
        JOB_NAMES.put("BUILDER", "建筑师");
        JOB_NAMES.put("MINER", "矿工");
        JOB_NAMES.put("LUMBERJACK", "伐木工");
        JOB_NAMES.put("COMMERCIAL", "商人");
        JOB_NAMES.put("INDUSTRIAL", "工人");
        JOB_NAMES.put("SHOPKEEPER", "店员");
        JOB_NAMES.put("GUARD", "守卫");
        JOB_NAMES.put("CHEF", "厨师");
        JOB_NAMES.put("TEACHER", "教师");
        JOB_NAMES.put("DOCTOR", "医生");
        JOB_NAMES.put("FISHERMAN", "渔夫");
        JOB_NAMES.put("SHEPHERD", "牧羊人");
    }

    private final ScrollablePanel scrollPanel = new ScrollablePanel();
    private EditBox searchBox;

    private List<SidebarDataSnapshot.CitizenRecord> allCitizens;
    private List<SidebarDataSnapshot.CitizenRecord> filtered;
    private String currentFilter = FILTER_ALL;
    private String searchText = "";

    public PopulationScreen() {
        super(Component.translatable("gui.xy2407_nsuk_addition.population.title"));
    }

    @Override
    protected void init() {
        super.init();
        allCitizens = SidebarDataSnapshot.get().citizens();

        int winX = (width - WINDOW_W) / 2;
        int winY = (height - WINDOW_H) / 2;

        Component hint = Component.translatable("gui.xy2407_nsuk_addition.population.search_hint");
        searchBox = new EditBox(font, winX + WINDOW_W - PADDING - SEARCH_W,
                winY + TITLE_H + 6, SEARCH_W, SEARCH_H, hint);
        searchBox.setHint(hint);
        searchBox.setMaxLength(32);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        applyFilter();
    }

    private void onSearchChanged(String text) {
        this.searchText = text == null ? "" : text;
        applyFilter();
    }

    private void applyFilter() {
        if (allCitizens == null) {
            allCitizens = List.of();
        }
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        filtered = allCitizens.stream()
                .filter(this::matchesFilter)
                .filter(c -> matchesSearch(c, query))
                .toList();
        recalcScroll();
    }

    private boolean matchesFilter(SidebarDataSnapshot.CitizenRecord c) {
        return switch (currentFilter) {
            case FILTER_EMPLOYED -> !"UNEMPLOYED".equals(c.jobType());
            case FILTER_UNEMPLOYED -> "UNEMPLOYED".equals(c.jobType());
            case FILTER_HOMED -> c.hasHome();
            case FILTER_HOMELESS -> !c.hasHome();
            default -> true;
        };
    }

    private static boolean matchesSearch(SidebarDataSnapshot.CitizenRecord c, String query) {
        if (query.isEmpty()) return true;
        return contains(c.name(), query)
                || contains(c.jobType(), query)
                || contains(getJobName(c.jobType()), query);
    }

    private static boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    private void recalcScroll() {
        int contentH = computeContentH();
        int totalH = filtered.size() * (ITEM_H + ITEM_GAP);
        scrollPanel.setContent(totalH, contentH);
    }

    private int computeContentH() {
        int winY = (height - WINDOW_H) / 2;
        int contentY = winY + TAB_Y + TAB_H + 6;
        return winY + WINDOW_H - contentY - PADDING;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        gg.fill(0, 0, width, height, 0xCC000000);

        int winX = (width - WINDOW_W) / 2;
        int winY = (height - WINDOW_H) / 2;

        gg.fill(winX, winY, winX + WINDOW_W, winY + WINDOW_H, BG_COLOR);
        gg.renderOutline(winX, winY, WINDOW_W, WINDOW_H, DIVIDER);

        gg.fill(winX + 1, winY + 1, winX + WINDOW_W - 1, winY + TITLE_H, TITLE_BAR_BG);
        gg.fill(winX + 1, winY + TITLE_H, winX + WINDOW_W - 1, winY + TITLE_H + 1, DIVIDER);
        Component titleText = getTitle();
        int titleW = font.width(titleText);
        gg.drawString(font, titleText, winX + (WINDOW_W - titleW) / 2, winY + 6, TEXT_PRIMARY, false);

        Component searchLabel = Component.literal("搜索:");
        gg.drawString(font, searchLabel, winX + PADDING, winY + TITLE_H + 8, TEXT_MUTED, false);

        int tabStartY = winY + TAB_Y;
        renderCategoryTabs(gg, mouseX, mouseY, winX + PADDING, tabStartY);

        String statsStr = "共 " + filtered.size() + " 人";
        if (!allCitizens.isEmpty() && !FILTER_ALL.equals(currentFilter)) {
            statsStr += " / " + allCitizens.size();
        }
        int statsW = font.width(statsStr);
        gg.drawString(font, Component.literal(statsStr),
                winX + WINDOW_W - PADDING - statsW, tabStartY + 2, TEXT_MUTED, false);

        int contentY = tabStartY + TAB_H + 6;
        int contentH = winY + WINDOW_H - contentY - PADDING;
        int cardW = scrollPanel.viewportWidth(WINDOW_W, PADDING);

        gg.enableScissor(winX + PADDING, contentY, winX + PADDING + cardW, contentY + contentH);

        if (filtered.isEmpty()) {
            Component emptyText = Component.translatable("gui.xy2407_nsuk_addition.population.empty");
            int emptyW = font.width(emptyText);
            gg.drawString(font, emptyText, winX + (WINDOW_W - emptyW) / 2,
                    contentY + contentH / 2 - font.lineHeight / 2, TEXT_MUTED, false);
        } else {
            int itemY = contentY - scrollPanel.scrollOffset();
            for (int i = 0; i < filtered.size(); i++) {
                if (itemY + ITEM_H > contentY && itemY < contentY + contentH) {
                    renderCitizenRow(gg, winX + PADDING, itemY, cardW, filtered.get(i), i);
                }
                itemY += ITEM_H + ITEM_GAP;
            }
        }

        gg.disableScissor();

        if (scrollPanel.isScrollbarVisible()) {
            int barX = scrollPanel.scrollbarX(winX + WINDOW_W, PADDING);
            scrollPanel.renderScrollbar(gg, barX, contentY, contentH, mouseX, mouseY);
        }

        if (searchBox != null) {
            searchBox.render(gg, mouseX, mouseY, partialTick);
        }
    }

    private void renderCitizenRow(GuiGraphics gg, int x, int y, int cardW,
                                   SidebarDataSnapshot.CitizenRecord citizen, int index) {
        int bg = (index % 2 == 0) ? CARD_BG : CARD_BG_ALT;
        gg.fill(x, y, x + cardW, y + ITEM_H, bg);

        int avatarY = y + (ITEM_H - AVATAR_SIZE) / 2;
        CitizenHeadRenderer.render(gg, citizen.skinPath(), x + 6, avatarY, AVATAR_SIZE, AVATAR_BORDER);

        gg.drawString(font, Component.literal(citizen.name()), x + 42, y + 5, TEXT_PRIMARY, false);

        String jobDisplay = getJobName(citizen.jobType());
        int jobColor = "UNEMPLOYED".equals(citizen.jobType()) ? COLOR_UNEMPLOYED : COLOR_EMPLOYED;
        gg.drawString(font, Component.literal(jobDisplay), x + 42, y + 20, jobColor, false);

        String homeStatus;
        int homeColor;
        if (citizen.hasHome()) {
            homeStatus = "有家";
            homeColor = COLOR_HOMED;
        } else {
            homeStatus = "无家";
            homeColor = TEXT_MUTED;
        }

        String colonyInfo = citizen.colonyName();
        if (colonyInfo != null && !colonyInfo.isEmpty()) {
            homeStatus = "附属:" + colonyInfo;
            homeColor = 0xFF66CCFF;
        }

        int hsW = font.width(homeStatus);
        gg.drawString(font, Component.literal(homeStatus), x + cardW - hsW - 10, y + (ITEM_H - font.lineHeight) / 2,
                homeColor, false);
    }

    private void renderCategoryTabs(GuiGraphics gg, int mouseX, int mouseY, int tabX, int tabY) {
        String[][] tabs = {
                {"全部", FILTER_ALL},
                {"有业", FILTER_EMPLOYED},
                {"无业", FILTER_UNEMPLOYED},
                {"有家", FILTER_HOMED},
                {"无家", FILTER_HOMELESS},
        };
        int tabW = 46;
        int tabGap = 4;

        for (int i = 0; i < tabs.length; i++) {
            String label = tabs[i][0];
            String filter = tabs[i][1];
            boolean active = filter.equals(currentFilter);
            int tX = tabX + i * (tabW + tabGap);

            boolean hovered = mouseX >= tX && mouseX <= tX + tabW && mouseY >= tabY && mouseY <= tabY + TAB_H;
            int color = active ? TAB_BG_ACTIVE : (hovered ? TAB_BG_HOVER : TAB_BG);

            gg.fill(tX, tabY, tX + tabW, tabY + TAB_H, color);
            gg.renderOutline(tX, tabY, tabW, TAB_H, active ? 0xFFFFFFFF : DIVIDER);

            int textW = font.width(label);
            gg.drawString(font, Component.literal(label),
                    tX + (tabW - textW) / 2, tabY + (TAB_H - font.lineHeight) / 2,
                    active ? TEXT_PRIMARY : TEXT_MUTED, false);
        }
    }

    private static String getJobName(String jobType) {
        if (jobType == null || jobType.isEmpty()) return "无业";
        return JOB_NAMES.getOrDefault(jobType, jobType);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        return scrollPanel.onMouseScrolled(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (scrollPanel.onMouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            int winY = (height - WINDOW_H) / 2;
            int tabY = winY + TAB_Y;
            int tabX = (width - WINDOW_W) / 2 + PADDING;
            String[][] tabs = {
                    {FILTER_ALL}, {FILTER_EMPLOYED}, {FILTER_UNEMPLOYED},
                    {FILTER_HOMED}, {FILTER_HOMELESS},
            };
            int tabW = 46, tabGap = 4;
            if (mouseY >= tabY && mouseY <= tabY + TAB_H) {
                for (int i = 0; i < tabs.length; i++) {
                    int tX = tabX + i * (tabW + tabGap);
                    if (mouseX >= tX && mouseX <= tX + tabW) {
                        currentFilter = tabs[i][0];
                        applyFilter();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollPanel.onMouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollPanel.onMouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PopulationScreen());
    }
}
