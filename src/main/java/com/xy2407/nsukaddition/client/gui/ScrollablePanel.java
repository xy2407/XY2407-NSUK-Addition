package com.xy2407.nsukaddition.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** 通用垂直滚动面板，管理滚动偏移、滚动条渲染与拖拽交互。 */
public final class ScrollablePanel {

    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_BG = 0xFF333333;
    private static final int SCROLLBAR_THUMB = 0xFF888888;
    private static final int SCROLLBAR_THUMB_HOVER = 0xFFAAAAAA;
    private static final int WHEEL_SPEED = 20;

    private int scrollOffset;
    private int maxScroll;
    private int contentHeight;
    private int viewportHeight;

    private boolean dragging;
    private int dragStartMouseY;
    private int dragStartScrollOffset;

    private int lastBarX, lastBarY, lastBarH, lastThumbY, lastThumbH;

    public void setContent(int contentHeight, int viewportHeight) {
        this.contentHeight = contentHeight;
        this.viewportHeight = viewportHeight;
        this.maxScroll = Math.max(0, contentHeight - viewportHeight);
        this.scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public boolean isScrollbarVisible() {
        return maxScroll > 0;
    }

    public int viewportWidth(int windowW, int padding) {
        return windowW - padding * 2 - SCROLLBAR_W - 4;
    }

    public int scrollbarX(int windowRightX, int padding) {
        return windowRightX - padding - SCROLLBAR_W - 8;
    }

    public boolean onMouseScrolled(double scrollY) {
        if (maxScroll <= 0) return true;
        scrollOffset -= (int) (scrollY * WHEEL_SPEED);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
        return true;
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || maxScroll <= 0) return false;
        if (mouseX >= lastBarX && mouseX <= lastBarX + SCROLLBAR_W
                && mouseY >= lastBarY && mouseY <= lastBarY + lastBarH) {
            if (mouseY >= lastThumbY && mouseY <= lastThumbY + lastThumbH) {
                dragging = true;
                dragStartMouseY = (int) mouseY;
                dragStartScrollOffset = scrollOffset;
            } else {

                int page = viewportHeight;
                if (mouseY < lastThumbY) {
                    scrollOffset = Math.max(0, scrollOffset - page);
                } else {
                    scrollOffset = Math.min(maxScroll, scrollOffset + page);
                }
            }
            return true;
        }
        return false;
    }

    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    public boolean onMouseDragged(double mouseX, double mouseY) {
        if (!dragging || maxScroll <= 0) return false;
        int available = lastBarH - lastThumbH;
        if (available <= 0) return true;
        int deltaMouse = (int) mouseY - dragStartMouseY;
        float ratioDelta = (float) deltaMouse / available;
        scrollOffset = Math.clamp(dragStartScrollOffset + (int) (ratioDelta * maxScroll), 0, maxScroll);
        return true;
    }

    public void renderScrollbar(GuiGraphics gg, int barX, int barY, int barH, int mouseX, int mouseY) {
        if (maxScroll <= 0) {
            lastBarX = lastBarY = lastBarH = lastThumbY = lastThumbH = 0;
            return;
        }
        this.lastBarX = barX;
        this.lastBarY = barY;
        this.lastBarH = barH;

        gg.fill(barX, barY, barX + SCROLLBAR_W, barY + barH, SCROLLBAR_BG);

        int thumbH = Math.max(20, (barH * barH) / contentHeight);
        float ratio = (float) scrollOffset / maxScroll;
        int thumbY = barY + (int) (ratio * (barH - thumbH));
        this.lastThumbY = thumbY;
        this.lastThumbH = thumbH;

        boolean hovered = mouseX >= barX && mouseX <= barX + SCROLLBAR_W
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int color = dragging || hovered ? SCROLLBAR_THUMB_HOVER : SCROLLBAR_THUMB;
        gg.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH, color);
    }
}
