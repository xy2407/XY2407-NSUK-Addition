package com.xy2407.nsukaddition.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingCacheService;
import com.xy2407.nsukaddition.client.hud.BatchRectRenderer;
import com.xy2407.nsukaddition.client.hud.BatchTextRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 底部建筑列表 HUD（侧边栏同款风格）：选中建筑师后显示，顶部搜索框 + 分类按钮 + 横向滚动卡片列表 + 滚动条。
 * 分类：原版 5 + nsuk 注入建筑盒的 breeding/cooking/foreign_trade；卡片文字左对齐。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsBuildingListHudLayer implements LayeredDraw.Layer {

    public static final RtsBuildingListHudLayer INSTANCE = new RtsBuildingListHudLayer();

    private static final BatchTextRenderer TEXT_RENDERER = new BatchTextRenderer();
    private static final BatchRectRenderer RECT_RENDERER = new BatchRectRenderer();

    private static final int PANEL_BG = 0xDD444444;
    private static final int PANEL_BORDER = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE6E6E6;
    private static final int TEXT_MUTED = 0xFFBDBDBD;
    private static final int BUTTON_BG = 0xFFE0E0E0;
    private static final int BUTTON_BG_HOVER = 0xFFFFFFFF;
    private static final int BUTTON_PRESSED = 0xFF71A4F4;
    private static final int BUTTON_TEXT = 0xFF333333;
    private static final int COLOR_ACCENT = 0xFF71A4F4;
    private static final int SEARCH_BG = 0xFF2A2A3A;
    private static final int SCROLLBAR_BG = 0xFF222222;

    private static final String[] CATEGORIES = {"residential", "commercial", "industry", "public", "other", "breeding", "cooking", "foreign_trade"};
    private static final String[] CATEGORY_NAMES = {"住宅", "商业", "工业", "公共", "其他", "养殖", "烹饪", "外贸"};

    private static final int PANEL_H = 128;
    private static final int CATEGORY_W = 74;
    private static final int CATEGORY_H = 24;
    private static final int CARD_W = 158;
    private static final int CARD_H = 62;
    private static final int CARD_GAP = 8;
    private static final int SCROLLBAR_H = 6;
    private static final int SEARCH_W = 150;
    private static final int SEARCH_H = 16;

    private static String selectedCategory;
    private static List<BuildingCacheService.BuildingMeta> currentBuildings = List.of();
    private static String query = "";
    private static boolean searchFocused;
    private static double scrollX;
    private static boolean draggingScrollbar;

    private static final Map<String, CategoryState> CATEGORY_STATES = new HashMap<>();

    private record CategoryState(double scrollX, String query) {
        CategoryState {
            query = query == null ? "" : query;
        }
    }

    public record Hit(int type, int index) {
        public boolean isCategory() {
            return type == 1;
        }

        public boolean isCard() {
            return type == 2;
        }

        public boolean isSearch() {
            return type == 0;
        }

        public boolean isScrollbar() {
            return type == 3;
        }
    }

    private RtsBuildingListHudLayer() {
    }

    public static boolean isSearchFocused() {
        return searchFocused;
    }

    public static boolean isListActive() {
        return selectedCategory != null && !RtsBuildingPlacementManager.isActive();
    }

    public static boolean isBlockingCameraKeys() {
        return searchFocused;
    }

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        if (!RtsModeManager.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null) {
            return;
        }
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float partial = dt.getGameTimeDeltaPartialTick(true);

        if (mc.options.hideGui) return;

        if (RtsBuildingPlacementManager.isActive()) {
            return;
        }

        UUID builderId = selectedBuilderId();
        if (builderId == null) {
            selectedCategory = null;
            currentBuildings = List.of();
            query = "";
            searchFocused = false;
            return;
        }

        int panelTop = screenH - PANEL_H - 8;
        int panelBottom = screenH - 4;
        RECT_RENDERER.begin(gg);
        TEXT_RENDERER.beginFrame(gg);
        try {
            flushPanel(panelTop, panelBottom, screenW);

            double[] mouse = RtsModeManager.getGuiScaledMouse();
            float mx = (float) mouse[0];
            float my = (float) mouse[1];

            drawSearch(mc, panelTop);
            drawCategories(mc, panelTop, mx, my);
            if (selectedCategory != null) {
                drawCards(mc, panelTop, mx, my, partial);
                drawScrollbar(mc, panelTop, screenW);
            }
        } finally {
            RECT_RENDERER.flush();
            TEXT_RENDERER.endFrame();
            gg.flush();
        }
    }

    private static void flushPanel(int panelTop, int panelBottom, int screenW) {
        RECT_RENDERER.fill(4, panelTop, screenW - 8, panelBottom - panelTop, PANEL_BG);
        RECT_RENDERER.outline(4, panelTop, screenW - 8, panelBottom - panelTop, PANEL_BORDER);
    }

    private static void drawSearch(Minecraft mc, int panelTop) {
        int x = 8;
        int y = panelTop + 4;
        int bg = searchFocused ? 0xFF3A3A50 : SEARCH_BG;
        RECT_RENDERER.fill(x, y, SEARCH_W, SEARCH_H, bg);
        if (searchFocused) {
            RECT_RENDERER.fill(x, y, SEARCH_W, 1, COLOR_ACCENT);
        }
        String display = query.isEmpty() ? (searchFocused ? "" : "\u00a77搜索建筑...") : query;
        TEXT_RENDERER.drawText(mc.font, display, x + 3, y + 4, TEXT_PRIMARY, false);
    }

    private static void drawCategories(Minecraft mc, int panelTop, float mx, float my) {
        int y = panelTop + 24;
        int x = 8;
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean hover = mx >= x && mx <= x + CATEGORY_W && my >= y && my <= y + CATEGORY_H;
            boolean selected = CATEGORIES[i].equals(selectedCategory);
            int bg = selected ? BUTTON_PRESSED : (hover ? BUTTON_BG_HOVER : BUTTON_BG);
            int textColor = selected ? TEXT_PRIMARY : BUTTON_TEXT;
            RECT_RENDERER.fill(x, y, CATEGORY_W, CATEGORY_H, bg);
            String label = CATEGORY_NAMES[i];
            int labelW = TEXT_RENDERER.calcWidth(mc.font, label);
            TEXT_RENDERER.drawText(mc.font, label, x + (CATEGORY_W - labelW) / 2.0F, y + (CATEGORY_H - 8) / 2.0F, textColor, false);
            x += CATEGORY_W + 6;
        }
    }

    private static void drawCards(Minecraft mc, int panelTop, float mx, float my, float partial) {
        int cardY = panelTop + 52;
        if (currentBuildings.isEmpty()) {
            String msg = "没有匹配的建筑";
            int msgW = TEXT_RENDERER.calcWidth(mc.font, msg);
            TEXT_RENDERER.drawText(mc.font, msg, (mc.getWindow().getGuiScaledWidth() - msgW) / 2.0F, cardY + (CARD_H - 8) / 2.0F, TEXT_MUTED, false);
            return;
        }
        int startX = 8;
        for (int i = 0; i < currentBuildings.size(); i++) {
            int cardX = (int) (startX - scrollX + i * (CARD_W + CARD_GAP));
            if (cardX + CARD_W < 8 || cardX > mc.getWindow().getGuiScaledWidth() - 8) {
                continue;
            }
            BuildingCacheService.BuildingMeta meta = currentBuildings.get(i);
            boolean hover = mx >= cardX && mx <= cardX + CARD_W && my >= cardY && my <= cardY + CARD_H;
            RECT_RENDERER.fill(cardX, cardY, CARD_W, CARD_H, hover ? 0xFF4A4A5C : 0xFF3A3A4A);
            String nameText = trim(mc, meta.name(), CARD_W - 16);
            String costText = "造价 " + meta.amount() + "  " + meta.size();
            String authorText = "作者：" + meta.author();
            TEXT_RENDERER.drawText(mc.font, nameText, cardX + (CARD_W - TEXT_RENDERER.calcWidth(mc.font, nameText)) / 2.0F, cardY + 5, TEXT_PRIMARY, false);
            TEXT_RENDERER.drawText(mc.font, costText, cardX + (CARD_W - TEXT_RENDERER.calcWidth(mc.font, costText)) / 2.0F, cardY + 18, TEXT_SECONDARY, false);
            TEXT_RENDERER.drawText(mc.font, authorText, cardX + (CARD_W - TEXT_RENDERER.calcWidth(mc.font, authorText)) / 2.0F, cardY + 31, TEXT_MUTED, false);
        }
    }

    private static void drawScrollbar(Minecraft mc, int panelTop, int screenW) {
        if (currentBuildings.isEmpty()) {
            return;
        }
        int y = panelTop + PANEL_H - SCROLLBAR_H - 2;
        int x = 8;
        int w = screenW - 16;
        RECT_RENDERER.fill(x, y, w, SCROLLBAR_H, SCROLLBAR_BG);
        int viewW = maxScroll();
        if (viewW > 0) {
            double ratio = scrollX / viewW;
            int thumbW = Math.max(20, w * viewportRatio() / 100);
            int thumbX = x + (int) (ratio * (w - thumbW));
            RECT_RENDERER.fill(thumbX, y, thumbW, SCROLLBAR_H, COLOR_ACCENT);
        } else {
            RECT_RENDERER.fill(x, y, w, SCROLLBAR_H, COLOR_ACCENT);
        }
    }

    private static int viewportRatio() {
        int viewW = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16;
        int total = currentBuildings.size() * (CARD_W + CARD_GAP) - CARD_GAP;
        if (total <= viewW) {
            return 1;
        }
        return Math.max(1, viewW * 100 / total);
    }

    private static int maxScroll() {
        if (currentBuildings.isEmpty()) {
            return 0;
        }
        int viewW = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16;
        int total = currentBuildings.size() * (CARD_W + CARD_GAP) - CARD_GAP;
        return Math.max(0, total - viewW);
    }

    public static void scrollBy(double vertical) {
        if (selectedCategory == null) {
            return;
        }
        scrollX = Math.max(0.0D, Math.min(maxScroll(), scrollX - vertical * 36.0D));
    }

    public static void scrollToRatio(double ratio) {
        if (selectedCategory == null) {
            return;
        }
        scrollX = Math.max(0.0D, Math.min(maxScroll(), ratio * maxScroll()));
    }

    public static void updateScrollbarDrag(float mouseX) {
        if (!draggingScrollbar || selectedCategory == null) {
            return;
        }
        int x = 8;
        int w = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16;
        int thumbW = Math.max(20, w * viewportRatio() / 100);
        double ratio = (mouseX - x - thumbW / 2.0D) / (double) (w - thumbW);
        scrollToRatio(Math.max(0.0D, Math.min(1.0D, ratio)));
    }

    public static void setDraggingScrollbar(boolean dragging) {
        draggingScrollbar = dragging;
    }

    public static Hit hitTest(double mouseX, double mouseY) {
        if (!RtsModeManager.isActive() || selectedBuilderId() == null) {
            return null;
        }
        if (RtsBuildingPlacementManager.isActive()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int panelTop = screenH - PANEL_H - 8;
        if (mouseY < panelTop || mouseY > screenH - 4) {
            return null;
        }
        if (mouseY >= panelTop + 4 && mouseY <= panelTop + 4 + SEARCH_H && mouseX >= 8 && mouseX <= 8 + SEARCH_W) {
            return new Hit(0, -1);
        }
        int catY = panelTop + 24;
        if (mouseY >= catY && mouseY <= catY + CATEGORY_H) {
            int x = 8;
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (mouseX >= x && mouseX <= x + CATEGORY_W) {
                    return new Hit(1, i);
                }
                x += CATEGORY_W + 6;
            }
        }
        if (selectedCategory != null && !currentBuildings.isEmpty()) {
            int scrollY = panelTop + PANEL_H - SCROLLBAR_H - 2;
            if (mouseY >= scrollY && mouseY <= scrollY + SCROLLBAR_H) {
                return new Hit(3, -1);
            }
        }
        if (selectedCategory != null) {
            int cardY = panelTop + 52;
            if (mouseY >= cardY && mouseY <= cardY + CARD_H) {
                for (int i = 0; i < currentBuildings.size(); i++) {
                    int cardX = (int) (8 - scrollX + i * (CARD_W + CARD_GAP));
                    if (mouseX >= cardX && mouseX <= cardX + CARD_W) {
                        return new Hit(2, i);
                    }
                }
            }
        }
        return null;
    }

    public static void handleClick(Hit hit) {
        if (hit == null) {
            searchFocused = false;
            return;
        }
        if (hit.isSearch()) {
            searchFocused = true;
            return;
        }
        if (hit.isCategory()) {
            int index = hit.index();
            if (index >= 0 && index < CATEGORIES.length) {
                String target = CATEGORIES[index];
                if (selectedCategory != null && !selectedCategory.equals(target)) {
                    CATEGORY_STATES.put(selectedCategory, new CategoryState(scrollX, query));
                }
                selectedCategory = target;
                CategoryState state = CATEGORY_STATES.get(selectedCategory);
                scrollX = state != null ? state.scrollX() : 0.0D;
                query = state != null ? state.query() : "";
                currentBuildings = new ArrayList<>(BuildingCacheService.getBuildings(selectedCategory));
                applyQuery();
                searchFocused = false;
            }
            return;
        }
        if (hit.isCard()) {
            int index = hit.index();
            if (index >= 0 && index < currentBuildings.size()) {
                BuildingCacheService.BuildingMeta meta = currentBuildings.get(index);
                UUID builderId = selectedBuilderId();
                if (builderId != null) {
                    Vec3 npcPos = findNpcPosition(builderId);
                    if (npcPos != null) {
                        RtsBuildingPlacementManager.startPlacement(builderId, meta, npcPos);
                    }
                }
            }
            return;
        }
        if (hit.isScrollbar()) {
            double[] mouse = RtsModeManager.getGuiScaledMouse();
            int x = 8;
            int w = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16;
            double ratio = (mouse[0] - x) / (double) w;
            scrollToRatio(Math.max(0.0D, Math.min(1.0D, ratio)));
            draggingScrollbar = true;
        }
    }

    public static void handleChar(int codePoint) {
        if (!searchFocused) {
            return;
        }
        if (codePoint == 259) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            }
        } else if (codePoint >= 32 && codePoint != 127) {
            query += new String(Character.toChars(codePoint));
        }
        applyQuery();
    }

    private static void applyQuery() {
        if (selectedCategory == null) {
            return;
        }
        String q = query.trim().toLowerCase();
        currentBuildings = new ArrayList<>(BuildingCacheService.getBuildings(selectedCategory));
        if (!q.isEmpty()) {
            currentBuildings.removeIf(meta -> meta.name() == null || !meta.name().toLowerCase().contains(q));
        }
    }

    public static UUID selectedBuilderId() {
        Set<UUID> selected = RtsModeManager.getSelectedEntities();
        if (selected == null || selected.size() != 1) {
            return null;
        }
        UUID id = selected.iterator().next();
        if (!RtsNpcCache.isReady()) {
            Entity e = findEntityById(id);
            return e instanceof common.cn.kafei.simukraft.entity.CitizenEntity citizen
                    && "BUILDER".equalsIgnoreCase(citizen.getJob()) ? id : null;
        }
        return RtsNpcCache.isBuilder(id) ? id : null;
    }

    public static boolean isBuilderSelected() {
        return selectedBuilderId() != null;
    }

    private static Vec3 findNpcPosition(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity.position();
            }
        }
        return null;
    }

    private static Entity findEntityById(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    private static String trim(Minecraft mc, String text, int maxWidth) {
        if (text == null || mc.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        return mc.font.plainSubstrByWidth(text, maxWidth - 6) + "...";
    }
}