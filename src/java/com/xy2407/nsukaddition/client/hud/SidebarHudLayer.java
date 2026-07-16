package com.xy2407.nsukaddition.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import java.util.ArrayList;
import java.util.List;

/** 侧边栏 HUD 主图层。 */
public final class SidebarHudLayer implements LayeredDraw.Layer {
    static final int REF_WIDTH = 320;

    static final int PANEL_BG = 0xDD444444;
    static final int PANEL_BORDER = 0xFF555555;
    static final int TITLE_BAR_BG = 0xFF303030;
    static final int DIVIDER = 0xFF555555;
    static final int TEXT_PRIMARY = 0xFFFFFFFF;
    static final int TEXT_SECONDARY = 0xFFE6E6E6;
    static final int TEXT_MUTED = 0xFFBDBDBD;
    static final int COLOR_SUCCESS = 0xFF66FF66;
    static final int COLOR_WARNING = 0xFFFFFF55;

    static final int BUTTON_BG = 0xFFE0E0E0;
    static final int BUTTON_BG_HOVER = 0xFFFFFFFF;
    static final int BUTTON_BORDER = 0xFFAAAAAA;
    static final int BUTTON_BORDER_HOVER = 0xFFFFFFFF;
    static final int BUTTON_PRESSED = 0xFF71A4F4;

    private static final String[] BUTTON_LABELS = {
            "hud.xy2407_nsuk_addition.sidebar.btn_build_tasks",
            "hud.xy2407_nsuk_addition.sidebar.btn_population",
            "hud.xy2407_nsuk_addition.sidebar.btn_funds_flow",
            "hud.xy2407_nsuk_addition.sidebar.btn_immigration"
    };

    private static final List<ButtonRect> buttonRects = new ArrayList<>();
    private static final BatchTextRenderer TEXT_RENDERER = new BatchTextRenderer();
    private static final BatchRectRenderer RECT_RENDERER = new BatchRectRenderer();
    private static final FooterCache FOOTER_CACHE = new FooterCache();

    static int mouseX;
    static int mouseY;

    public record ButtonRect(int x, int y, int width, int height, int index) {}

    private static int selectedIndex = -1;
    private static final PopupAnimation ANIMATION = new PopupAnimation();
    public static final SidebarHudLayer INSTANCE = new SidebarHudLayer();

    private SidebarHudLayer() {}

    public static void cycleSelection(int delta) {
        if (delta == 0) return;
        int count = BUTTON_LABELS.length;
        if (selectedIndex < 0) {
            selectedIndex = delta > 0 ? 0 : count - 1;
        } else {
            selectedIndex = (selectedIndex + delta + count) % count;
        }
    }

    public static int getSelectedIndex() { return selectedIndex; }

    public static void clearSelection() { selectedIndex = -1; }

    public static void executeSelectedButton() {
        if (selectedIndex < 0) return;
        int idx = selectedIndex;
        clearSelection();
        switch (idx) {
            case 0 -> BuildTaskScreen.open();
            case 1 -> PopulationScreen.open();
            case 2 -> FundsFlowScreen.open();
            case 3 -> ImmigrationScreen.open();
        }
    }

    public void toggle() {
        ANIMATION.toggle();
        if (ANIMATION.visible() && ANIMATION.progress() <= 0f) clearSelection();
    }

    public void updateAnimationFrame() { ANIMATION.updateFrame(); }

    public static boolean isVisible() {
        return ANIMATION.visible() && Minecraft.getInstance().screen == null;
    }

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        ANIMATION.updateFrame();
        if (!ANIMATION.visible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        double scale = mc.getWindow().getGuiScale();
        mouseX = (int) (mc.mouseHandler.xpos() / scale);
        mouseY = (int) (mc.mouseHandler.ypos() / scale);

        float eased = easeOutCubic(ANIMATION.progress());
        PopupLayoutManager.Rect r = PopupLayoutManager.compute(gg.guiWidth(), gg.guiHeight(), eased);
        float cs = (float) r.width() / REF_WIDTH;
        float designH = r.height() / cs;
        int alpha = clampAlpha(eased);

        gg.pose().pushPose();
        gg.pose().translate(r.x(), r.y(), 0);
        gg.pose().scale(cs, cs, 1.0f);

        RECT_RENDERER.begin(gg);
        TEXT_RENDERER.beginFrame(gg);
        try {
            renderPanel(designH, alpha, mc);
            int y = HeaderSectionRenderer.render(gg, mc, TEXT_RENDERER, RECT_RENDERER);
            y = BuildStatsSectionRenderer.render(mc, y, TEXT_RENDERER, RECT_RENDERER);
            QuestSectionRenderer.render(gg, mc, y, TEXT_RENDERER, RECT_RENDERER);
            renderFooter(designH, mc);
        } finally {
            RECT_RENDERER.flush();
            TEXT_RENDERER.endFrame();
            gg.pose().popPose();
        }
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static void renderPanel(float designH, int alpha, Minecraft mc) {
        int bg = FastColor.ARGB32.color((int) (alpha * 0.867f), 0x44, 0x44, 0x44);
        int border = FastColor.ARGB32.color(alpha, 0x55, 0x55, 0x55);
        int titleBar = FastColor.ARGB32.color((int) (alpha * 0.8f), 0x30, 0x30, 0x30);
        int textColor = FastColor.ARGB32.color(alpha, 0xFF, 0xFF, 0xFF);

        RECT_RENDERER.fill(0, 0, REF_WIDTH, designH, bg);
        RECT_RENDERER.outline(0, 0, REF_WIDTH, designH, border);
        RECT_RENDERER.fill(1, 1, REF_WIDTH - 2, SidebarLayout.TOP_BAR_H - 1, titleBar);
        RECT_RENDERER.fill(1, SidebarLayout.TOP_BAR_H, REF_WIDTH - 2, 1, border);

        String title = Component.translatable("hud.xy2407_nsuk_addition.sidebar.title").getString()
                + "  " + HeaderSectionRenderer.buildTitleExtra(mc);
        TEXT_RENDERER.drawText(mc.font, title, SidebarLayout.PAD_X, 6, textColor, false);
    }

    private static void renderFooter(float designH, Minecraft mc) {
        float padX = SidebarLayout.PAD_X;
        float buttonGap = SidebarLayout.BUTTON_GAP;
        float buttonH = SidebarLayout.BUTTON_H;
        float buttonTopGap = SidebarLayout.BUTTON_TOP_GAP;
        float totalContentW = REF_WIDTH - padX * 2;
        float buttonW = (totalContentW - buttonGap * 3) / 4f;

        float footerY = designH - SidebarLayout.FOOTER_H;
        float buttonY = footerY + buttonTopGap;

        RECT_RENDERER.fill(padX, footerY, REF_WIDTH - padX * 2, 1, DIVIDER);

        FOOTER_CACHE.ensure(mc);
        synchronized (buttonRects) {
            buttonRects.clear();
            for (int i = 0; i < 4; i++) {
                float bx = padX + i * (buttonW + buttonGap);
                boolean hovered = mouseX >= rX(bx) && mouseX <= rX(bx + buttonW)
                        && mouseY >= rY(buttonY) && mouseY <= rY(buttonY + buttonH);
                boolean selected = selectedIndex == i;
                buttonRects.add(new ButtonRect(Math.round(bx), Math.round(buttonY),
                        Math.round(buttonW), Math.round(buttonH), i));

                int bg = selected ? BUTTON_PRESSED : (hovered ? BUTTON_BG_HOVER : BUTTON_BG);
                int borderC = selected ? BUTTON_PRESSED : (hovered ? BUTTON_BORDER_HOVER : BUTTON_BORDER);
                int textColor = selected ? 0xFFFFFFFF : 0xFF333333;

                RECT_RENDERER.fill(bx, buttonY, buttonW, buttonH, bg);
                RECT_RENDERER.outline(bx, buttonY, buttonW, buttonH, borderC);

                String label = FOOTER_CACHE.labels[i];
                int labelW = FOOTER_CACHE.widths[i];
                float textX = bx + (buttonW - labelW) / 2f;
                float textY = buttonY + (buttonH - mc.font.lineHeight) / 2f;
                TEXT_RENDERER.drawText(mc.font, label, textX, textY, textColor, false);

                if (selected) {
                    RECT_RENDERER.fill(bx + 4, buttonY + buttonH - 2, buttonW - 8, 1, 0xFFFFFFFF);
                }
            }
        }
    }

    static float cs() {
        PopupLayoutManager.Rect r = PopupLayoutManager.compute(
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight(), 1f);
        return (float) r.width() / REF_WIDTH;
    }

    static int rX(float designX) {
        return Math.round(designX * cs());
    }

    static int rY(float designY) {
        PopupLayoutManager.Rect r = PopupLayoutManager.compute(
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight(), 1f);
        return r.y() + Math.round(designY * cs());
    }

    private static int clampAlpha(float progress) {
        return Math.clamp((int) (progress * 255f), 0, 255);
    }

    private static final class FooterCache {
        private final String[] labels = new String[BUTTON_LABELS.length];
        private final int[] widths = new int[BUTTON_LABELS.length];
        private String languageKey = "";

        void ensure(Minecraft mc) {
            String cur = mc.getLanguageManager().getSelected();
            if (cur.equals(languageKey)) return;
            languageKey = cur;
            for (int i = 0; i < BUTTON_LABELS.length; i++) {
                labels[i] = Component.translatable(BUTTON_LABELS[i]).getString();
                widths[i] = TEXT_RENDERER.calcWidth(mc.font, labels[i]);
            }
        }
    }
}
