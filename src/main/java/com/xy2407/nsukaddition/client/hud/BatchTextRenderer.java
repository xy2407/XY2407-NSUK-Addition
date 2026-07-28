package com.xy2407.nsukaddition.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** 批量文本渲染器，每帧所有文本共享一次缓冲区，避免逐行 flush。 */
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
        font.drawInBatch(text, x, (float) Math.round(y), color, shadow,
                guiGraphics.pose().last().pose(), guiGraphics.bufferSource(),
                Font.DisplayMode.SEE_THROUGH, 15728640, 0);
    }

    public void drawText(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f matrix) {
        if (guiGraphics == null || text == null || text.isEmpty()) return;
        font.drawInBatch(text, x, (float) Math.round(y), color, shadow,
                matrix, guiGraphics.bufferSource(),
                Font.DisplayMode.SEE_THROUGH, 15728640, 0);
    }

    public void endFrame() {
        guiGraphics = null;
    }
}
