package com.xy2407.nsukaddition.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/** 批量矩形渲染器，纯包装，不做任何缩放变换（由 PoseStack 处理）。 */
final class BatchRectRenderer {
    private GuiGraphics guiGraphics;

    void begin(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
    }

    void flush() {
        guiGraphics = null;
    }

    void fill(float x, float y, float width, float height, int color) {
        if (guiGraphics == null || width <= 0 || height <= 0) return;
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        if (right > left && bottom > top) {
            guiGraphics.fill(left, top, right, bottom, color);
        }
    }

    void outline(float x, float y, float width, float height, int color) {
        fill(x, y, width, 1, color);
        fill(x, y + height - 1, width, 1, color);
        fill(x, y, 1, height, color);
        fill(x + width - 1, y, 1, height, color);
    }
}
