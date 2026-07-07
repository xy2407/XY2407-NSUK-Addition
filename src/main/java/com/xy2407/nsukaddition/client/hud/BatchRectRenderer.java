package com.xy2407.nsukaddition.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/** 批量矩形渲染器，在同一帧内绘制填充矩形和描边矩形。 */
final class BatchRectRenderer {
    private GuiGraphics guiGraphics;

    void begin(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
    }

    void fill(Matrix4f matrix, float x, float y, float width, float height, int color) {
        if (guiGraphics == null || width <= 0 || height <= 0) {
            return;
        }
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        if (right <= left || bottom <= top) {
            return;
        }
        guiGraphics.fill(left, top, right, bottom, color);
    }

    void outline(Matrix4f matrix, float x, float y, float width, float height, int color) {
        fill(matrix, x, y, width, 1, color);
        fill(matrix, x, y + height - 1, width, 1, color);
        fill(matrix, x, y, 1, height, color);
        fill(matrix, x + width - 1, y, 1, height, color);
    }

    void flush() {
        guiGraphics = null;
    }
}
