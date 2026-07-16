package com.xy2407.nsukaddition.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** 批量文本渲染器，纯包装，不做任何缩放变换（由 PoseStack 处理）。 */
public final class BatchTextRenderer {
    private static final ResourceLocation DEFAULT_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "default");

    private GuiGraphics guiGraphics;

    public void beginFrame(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
    }

    public int calcWidth(Font font, String text) {
        if (text == null || text.isEmpty()) return 0;
        FontSet fontSet = font.getFontSet(DEFAULT_FONT);
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += Math.round(fontSet.getGlyphInfo(text.charAt(i), true).getAdvance());
        }
        return width;
    }

    public void drawText(Font font, String text, float x, float y, int color, boolean shadow) {
        if (guiGraphics == null || text == null || text.isEmpty()) return;
        guiGraphics.drawString(font, text, Math.round(x), Math.round(y), color, shadow);
    }

    public void drawText(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f matrix) {
        drawText(font, text, x, y, color, shadow);
    }

    public void endFrame() {
        guiGraphics = null;
    }
}
