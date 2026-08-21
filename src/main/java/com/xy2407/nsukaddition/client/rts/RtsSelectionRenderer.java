package com.xy2407.nsukaddition.client.rts;

import com.xy2407.nsukaddition.client.hud.BatchTextRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/** RTS 模式 HUD 图层：绘制十字准星、框选矩形、左上角选中信息、锁定按钮和 NPC 头顶血量。 */
public final class RtsSelectionRenderer implements LayeredDraw.Layer {

    public static final RtsSelectionRenderer INSTANCE = new RtsSelectionRenderer();

    private static final BatchTextRenderer textRenderer = new BatchTextRenderer();

    private static final int BOX_BORDER = 0xFF00DDFF;
    private static final int BOX_FILL = 0x3300DDFF;
    private static final int HINT_COLOR = 0xFF66FF66;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int CROSSHAIR_COLOR = 0xFFFFFFFF;
    private static final int LOCK_COLOR = 0xFFFFFF55;
    private static final int UNLOCK_COLOR = 0xFF55FF55;

    private static final int LOCK_BTN_X = 8;
    private static final int LOCK_BTN_Y = 148;
    private static final int LOCK_BTN_W = 44;
    private static final int LOCK_BTN_H = 14;
    private static final int UNLOCK_BTN_X = LOCK_BTN_X;
    private static final int UNLOCK_BTN_Y = LOCK_BTN_Y + 18;

    private RtsSelectionRenderer() {
    }

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        if (!RtsModeManager.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        RtsInputHandler.onFrame();

        if (mc.options.hideGui) return;

        textRenderer.beginFrame(gg);

        double[] mousePos = RtsModeManager.getGuiScaledMouse();
        int mx = (int) mousePos[0];
        int my = (int) mousePos[1];
        drawCrosshair(gg, mx, my);

        if (RtsModeManager.isBoxSelecting()) {
            double x1 = RtsModeManager.getBoxStartX();
            double y1 = RtsModeManager.getBoxStartY();
            double x2 = RtsModeManager.getBoxEndX();
            double y2 = RtsModeManager.getBoxEndY();
            int minX = (int) Math.min(x1, x2);
            int minY = (int) Math.min(y1, y2);
            int maxX = (int) Math.max(x1, x2);
            int maxY = (int) Math.max(y1, y2);
            int w = maxX - minX;
            int h = maxY - minY;
            if (w > 0 && h > 0) {
                int fill = RtsModeManager.isCtrlBoxSelecting() ? 0x33FF4444 : BOX_FILL;
                int border = RtsModeManager.isCtrlBoxSelecting() ? 0xFFFF4444 : BOX_BORDER;
                gg.fill(minX, minY, maxX, maxY, fill);
                drawBorder(gg, minX, minY, maxX, maxY, border);
            }
        }

        int selectedCount = RtsModeManager.getSelectedEntities().size();
        int y = 28;
        textRenderer.drawText(mc.font, "\u00a7eRTS \u6307\u6325\u6a21\u5f0f", 8, y, TITLE_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7a\u5de6\u952e\u6846\u9009/\u70b9\u9009\u5b9e\u4f53", 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7a\u53f3\u952e\u547d\u4ee4\u79fb\u52a8", 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7a\u4e2d\u952e/\u6ed1\u8f6e\u8c03\u6574\u89c6\u89d2", 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7a\u75be\u8dd1\u952e\u52a0\u901f\u79fb\u52a8", 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7aF7 \u6b63\u4ea4\u89c6\u89d2", 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7a\u5df2\u9009\u4e2d: " + selectedCount, 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7aCtrl+\u5de6\u952e\u6846\u9009\u653b\u51fb\u76ee\u6807", 8, y, HINT_COLOR, false);
        y += 12;
        textRenderer.drawText(mc.font, "\u00a7a~\u952e\u6e05\u9664\u653b\u51fb\u76ee\u6807", 8, y, HINT_COLOR, false);

        drawLockButtons(gg);
        drawFormationButtons(gg);
        drawViewButtons(gg);

        drawModeHint(gg, mc);

        textRenderer.endFrame();
    }

    private static void drawLockButtons(GuiGraphics gg) {
        Minecraft mc = Minecraft.getInstance();
        boolean locked = RtsModeManager.isSelectionLocked();
        gg.fill(LOCK_BTN_X - 1, LOCK_BTN_Y - 1, LOCK_BTN_X + LOCK_BTN_W + 1, LOCK_BTN_Y + LOCK_BTN_H + 1, 0xFF000000);
        gg.fill(LOCK_BTN_X, LOCK_BTN_Y, LOCK_BTN_X + LOCK_BTN_W, LOCK_BTN_Y + LOCK_BTN_H,
                locked ? 0xFF888800 : 0xFF333300);
        textRenderer.drawText(mc.font, "\u00a7e\u9501\u5b9a", LOCK_BTN_X + 14, LOCK_BTN_Y + 3, LOCK_COLOR, false);
        gg.fill(UNLOCK_BTN_X - 1, UNLOCK_BTN_Y - 1, LOCK_BTN_X + LOCK_BTN_W + 1, UNLOCK_BTN_Y + LOCK_BTN_H + 1, 0xFF000000);
        gg.fill(UNLOCK_BTN_X, UNLOCK_BTN_Y, LOCK_BTN_X + LOCK_BTN_W, UNLOCK_BTN_Y + LOCK_BTN_H,
                locked ? 0xFF003300 : 0xFF005500);
        textRenderer.drawText(mc.font, "\u00a7a\u89e3\u9501", LOCK_BTN_X + 14, UNLOCK_BTN_Y + 3, UNLOCK_COLOR, false);
    }

    private static final int FORM_BTN_X = LOCK_BTN_X;
    private static final int FORM_BTN_Y0 = UNLOCK_BTN_Y + 18;
    private static final int FORM_BTN_W = LOCK_BTN_W;
    private static final int FORM_BTN_H = LOCK_BTN_H;
    private static final int FORM_BTN_GAP = 18;
    private static final String[] FORM_LABELS = {"\u00a77\u9ed8\u8ba4", "\u00a77\u76f4\u7ebf", "\u00a77\u65b9\u5f62", "\u00a77\u4e09\u89d2"};

    private static void drawFormationButtons(GuiGraphics gg) {
        Minecraft mc = Minecraft.getInstance();
        int current = RtsModeManager.getFormation().ordinal();
        for (int i = 0; i < FORM_LABELS.length; i++) {
            int by = FORM_BTN_Y0 + i * FORM_BTN_GAP;
            gg.fill(FORM_BTN_X - 1, by - 1, FORM_BTN_X + FORM_BTN_W + 1, by + FORM_BTN_H + 1, 0xFF000000);
            gg.fill(FORM_BTN_X, by, FORM_BTN_X + FORM_BTN_W, by + FORM_BTN_H,
                    i == current ? 0xFF444488 : 0xFF333344);
            textRenderer.drawText(mc.font, FORM_LABELS[i], FORM_BTN_X + 10, by + 3, 0xFFFFFFFF, false);
        }
    }

    private static final int VIEW_BTN_X = FORM_BTN_X;
    private static final int VIEW_BTN_Y0 = FORM_BTN_Y0 + FORM_LABELS.length * FORM_BTN_GAP + 6;
    private static final int VIEW_BTN_W = FORM_BTN_W;
    private static final int VIEW_BTN_H = FORM_BTN_H;
    private static final int VIEW_BTN_GAP = 18;
    private static final String[] VIEW_LABELS = {"\u00a7b60\u00b0\u659c\u89c6", "\u00a7b45\u00b0\u659c\u89c6", "\u00a7a\u81ea\u7531\u89c6\u89d2"};

    private static void drawViewButtons(GuiGraphics gg) {
        Minecraft mc = Minecraft.getInstance();
        RtsModeManager.RtsViewMode mode = RtsModeManager.getViewMode();
        for (int i = 0; i < VIEW_LABELS.length; i++) {
            int by = VIEW_BTN_Y0 + i * VIEW_BTN_GAP;
            boolean active = (i == 0 && mode == RtsModeManager.RtsViewMode.ISO_60)
                    || (i == 1 && mode == RtsModeManager.RtsViewMode.ISO_45)
                    || (i == 2 && mode == RtsModeManager.RtsViewMode.FREE);
            gg.fill(VIEW_BTN_X - 1, by - 1, VIEW_BTN_X + VIEW_BTN_W + 1, by + VIEW_BTN_H + 1, 0xFF000000);
            gg.fill(VIEW_BTN_X, by, VIEW_BTN_X + VIEW_BTN_W, by + VIEW_BTN_H,
                    active ? 0xFF448844 : 0xFF334433);
            textRenderer.drawText(mc.font, VIEW_LABELS[i], VIEW_BTN_X + 8, by + 3, 0xFFFFFFFF, false);
        }
    }

    private static void drawModeHint(GuiGraphics gg, Minecraft mc) {
        if (RtsBuildingPlacementManager.isActive()) {
            drawCenteredHint(gg, mc, "\u00a7e\u653e\u7f6e\u6a21\u5f0f \u2014 \u5efa\u9020\u5efa\u7b51",
                    "\u00a7a\u6eda\u8f6e = \u8c03\u6574\u9ad8\u5ea6",
                    "\u00a7aR = \u65cb\u8f6c",
                    "\u00a7a\u5de6\u952e\u62d6\u62fd\u6295\u5f71 = \u5e73\u79fb",
                    "\u00a7a\u53f3\u952e = \u786e\u8ba4\u653e\u7f6e\uff08\u52a0\u5165\u5f85\u6ce8\u5165\uff09",
                    "\u00a7aC = \u64a4\u56de\u6700\u540e\u4e00\u4e2a\u653e\u7f6e",
                    "\u00a7aEnter = \u6700\u7ec8\u786e\u8ba4\u653e\u7f6e");
        } else if (RtsBuildingPlacementManager.isMoveActive()) {
            drawCenteredHint(gg, mc, "\u00a7e\u8fc1\u79fb\u6a21\u5f0f \u2014 \u8fc1\u79fb\u5efa\u7b51",
                    "\u00a7aEsc = \u9000\u51fa",
                    "\u00a7a\u5de6\u952e\u6309\u4f4f\u62d6\u62fd = \u6c34\u5e73\u5e73\u79fb",
                    "\u00a7a\u5de6\u952e\u6309\u4f4f + \u6eda\u8f6e = \u8c03\u6574\u9ad8\u5ea6",
                    "\u00a7aR = \u65cb\u8f6c",
                    "\u00a7aEnter = \u786e\u8ba4\u8fc1\u79fb");
        } else if (RtsBuildingListHudLayer.isBuilderSelected()) {
            drawCenteredHint(gg, mc, "\u00a7e\u5efa\u7b51\u5e08\u5df2\u5c31\u7eea \u2014 \u4e0b\u9762\u9009\u62e9\u5efa\u7b51",
                    "\u00a7a\u70b9\u51fb\u5e95\u90e8\u5206\u7c7b/\u5361\u7247 = \u8fdb\u5165\u653e\u7f6e\u6a21\u5f0f",
                    "\u00a7aR = \u65cb\u8f6c",
                    "\u00a7a\u6eda\u8f6e = \u8c03\u6574\u9ad8\u5ea6",
                    "\u00a7a\u53f3\u952e = \u786e\u8ba4\u653e\u7f6e\uff08\u52a0\u5165\u5f85\u6ce8\u5165\uff09",
                    "\u00a7aEnter = \u6700\u7ec8\u786e\u8ba4\u653e\u7f6e");
        }
    }

    private static void drawCenteredHint(GuiGraphics gg, Minecraft mc, String title, String... lines) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int lineHeight = mc.font.lineHeight;
        int lineGap = 2;
        int titleGap = 4;
        int y0 = screenH / 3 - lineHeight / 2;
        textRenderer.drawText(mc.font, title, screenW / 2 - mc.font.width(title) / 2, y0, TITLE_COLOR, false);
        int y = y0 + lineHeight + titleGap;
        for (String line : lines) {
            textRenderer.drawText(mc.font, line, screenW / 2 - mc.font.width(line) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight + lineGap;
        }
    }

    public static int hitTestViewButton(double mouseX, double mouseY) {
        for (int i = 0; i < VIEW_LABELS.length; i++) {
            int by = VIEW_BTN_Y0 + i * VIEW_BTN_GAP;
            if (mouseX >= VIEW_BTN_X && mouseX <= VIEW_BTN_X + VIEW_BTN_W
                    && mouseY >= by && mouseY <= by + VIEW_BTN_H) {
                return i;
            }
        }
        return -1;
    }

    public static int hitTestFormationButton(double mouseX, double mouseY) {
        for (int i = 0; i < FORM_LABELS.length; i++) {
            int by = FORM_BTN_Y0 + i * FORM_BTN_GAP;
            if (mouseX >= FORM_BTN_X && mouseX <= FORM_BTN_X + FORM_BTN_W
                    && mouseY >= by && mouseY <= by + FORM_BTN_H) {
                return i;
            }
        }
        return -1;
    }

    public static int hitTestLockButton(double mouseX, double mouseY) {
        if (mouseX >= LOCK_BTN_X && mouseX <= LOCK_BTN_X + LOCK_BTN_W
                && mouseY >= LOCK_BTN_Y && mouseY <= LOCK_BTN_Y + LOCK_BTN_H) {
            return 0;
        }
        if (mouseX >= LOCK_BTN_X && mouseX <= LOCK_BTN_X + LOCK_BTN_W
                && mouseY >= UNLOCK_BTN_Y && mouseY <= UNLOCK_BTN_Y + LOCK_BTN_H) {
            return 1;
        }
        return -1;
    }

    private static void drawCrosshair(GuiGraphics gg, int x, int y) {
        gg.fill(x - 1, y - 1, x + 2, y + 2, CROSSHAIR_COLOR);
        gg.fill(x - 8, y, x - 2, y + 1, CROSSHAIR_COLOR);
        gg.fill(x + 3, y, x + 9, y + 1, CROSSHAIR_COLOR);
        gg.fill(x, y - 8, x + 1, y - 2, CROSSHAIR_COLOR);
        gg.fill(x, y + 3, x + 1, y + 9, CROSSHAIR_COLOR);
    }

    private static void drawBorder(GuiGraphics gg, int minX, int minY, int maxX, int maxY, int color) {
        gg.fill(minX, minY, maxX, minY + 1, color);
        gg.fill(minX, maxY - 1, maxX, maxY, color);
        gg.fill(minX, minY, minX + 1, maxY, color);
        gg.fill(maxX - 1, minY, maxX, maxY, color);
    }
}