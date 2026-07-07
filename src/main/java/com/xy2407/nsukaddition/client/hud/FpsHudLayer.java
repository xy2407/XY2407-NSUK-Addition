package com.xy2407.nsukaddition.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

/** 屏幕顶部居中的 FPS 显示图层。 */
public final class FpsHudLayer implements LayeredDraw.Layer {

    public static final FpsHudLayer INSTANCE = new FpsHudLayer();

    private static final BatchTextRenderer TEXT_RENDERER = new BatchTextRenderer();

    private String cachedText = "";
    private int cachedWidth = 0;
    private int lastFps = -1;

    private FpsHudLayer() {
    }

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        int fps = mc.getFps();

        if (fps != lastFps) {
            lastFps = fps;
            cachedText = "fps：" + fps;
            cachedWidth = TEXT_RENDERER.calcWidth(mc.font, cachedText);
        }

        if (cachedText.isEmpty()) {
            return;
        }

        Matrix4f matrix = gg.pose().last().pose();
        int screenWidth = gg.guiWidth();
        int color = FastColor.ARGB32.color(0xFF, 0xFF, 0xFF, 0xFF);

        TEXT_RENDERER.beginFrame(gg);
        try {
            float x = (screenWidth - cachedWidth) / 2.0F;
            TEXT_RENDERER.drawText(mc.font, cachedText, x, 2, color, true, matrix);
        } finally {
            TEXT_RENDERER.endFrame();
        }
    }
}
