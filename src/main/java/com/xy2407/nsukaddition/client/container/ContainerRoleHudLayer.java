package com.xy2407.nsukaddition.client.container;

import com.xy2407.nsukaddition.client.hud.BatchTextRenderer;
import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

public final class ContainerRoleHudLayer implements LayeredDraw.Layer {

    public static final ContainerRoleHudLayer INSTANCE = new ContainerRoleHudLayer();

    private static final BatchTextRenderer TEXT_RENDERER = new BatchTextRenderer();
    private static final int CARD_WIDTH = 180;
    private static final int CARD_PADDING = 8;
    private static final int LINE_GAP = 12;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int TEXT_COLOR  = 0xFFEEEEEE;
    private static final int COORD_COLOR = 0xFF55FFFF;
    private static final int BG_COLOR    = 0xCC222222;
    private static final int BORDER_COLOR = 0x88FFAA00;

    private float animationProgress;
    private long lastResponseTime;

    private ContainerRoleHudLayer() {}

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        ContainerRoleResponsePacket response = ContainerRoleClientCache.getResponse();
        if (response == null) {
            animationProgress = 0.0F;
            return;
        }

        long now = System.currentTimeMillis();
        if (lastResponseTime == 0) lastResponseTime = now;

        float target = response.equals(ContainerRoleClientCache.getResponse()) ? 1.0F : 0.0F;
        animationProgress += (target - animationProgress) * 0.15F;
        if (animationProgress < 0.01F) return;

        Font font = mc.font;
        int sw = gg.guiWidth();
        int sh = gg.guiHeight();

        int cx = sw / 2;
        int cy = sh / 2;

        String title = switch (response.role()) {
            case "input" -> "\u6750\u6599\u8F93\u5165\u5904";
            case "output" -> "\u4EA7\u51FA\u5B58\u653E\u5904";
            default -> "\u5BB9\u5668";
        };

        String coords = "[" + response.relativeX() + " " + response.relativeY() + " " + response.relativeZ() + "]";
        String desc = switch (response.role()) {
            case "input" -> "\u8BF7\u5C06\u6750\u6599\u653E\u5165\u5176\u4E2D";
            case "output" -> "\u4EA7\u51FA\u5C06\u4ECE\u6B64\u5904\u6536\u96C6";
            default -> "";
        };

        int titleW = font.width(title);
        int coordW = font.width(coords);
        int descW = font.width(desc);
        int contentW = Math.max(titleW, Math.max(coordW, descW));
        int cardW = Math.min(contentW + CARD_PADDING * 2, CARD_WIDTH);
        int cardH = (desc.isEmpty() ? 2 : 3) * LINE_GAP + CARD_PADDING * 2;

        int cardX = (int)(cx + 20 - (1.0F - animationProgress) * 30.0F);
        int cardY = cy - cardH / 2;

        int alpha = (int)(animationProgress * 200) << 24;
        int bg = (BG_COLOR & 0x00FFFFFF) | alpha;
        int border = (BORDER_COLOR & 0x00FFFFFF) | Math.min(alpha, 0x88000000);

        Matrix4f matrix = gg.pose().last().pose();

        gg.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
        gg.fill(cardX, cardY, cardX + cardW, cardY + 1, border);
        gg.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, border);
        gg.fill(cardX, cardY, cardX + 1, cardY + cardH, border);
        gg.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, border);

        TEXT_RENDERER.beginFrame(gg);
        try {
            int textX = cardX + CARD_PADDING;
            int textY = cardY + CARD_PADDING;

            TEXT_RENDERER.drawText(font, title, textX, textY, TITLE_COLOR, true, matrix);
            textY += LINE_GAP;
            TEXT_RENDERER.drawText(font, coords, textX, textY, COORD_COLOR, true, matrix);
            if (!desc.isEmpty()) {
                textY += LINE_GAP;
                TEXT_RENDERER.drawText(font, desc, textX, textY, TEXT_COLOR, true, matrix);
            }
        } finally {
            TEXT_RENDERER.endFrame();
        }
    }
}
