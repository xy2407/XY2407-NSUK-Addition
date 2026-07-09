package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

/** 核心迁移模式 HUD：屏幕中央提示文字，告知玩家当前处于迁移模式及操作方式。 */
public final class CoreMoveHudLayer implements LayeredDraw.Layer {
    public static final CoreMoveHudLayer INSTANCE = new CoreMoveHudLayer();

    private static final int PANEL_W = 320;
    private static final int LINE_H = 14;
    private static final int PAD_Y = 12;
    private static final int PAD_X = 18;
    private static final int TITLE_H = 22;

    private static final int BG_COLOR   = 0xCC222222;
    private static final int BORDER_CLR = 0xFF666666;
    private static final int TITLE_BG   = 0xFF404040;
    private static final int TEXT_TITLE = 0xFFFFAA00;
    private static final int TEXT_BODY  = 0xFFFFFFFF;

    private final BatchTextRenderer textRenderer = new BatchTextRenderer();
    private final BatchRectRenderer rectRenderer = new BatchRectRenderer();

    private CoreMoveHudLayer() {}

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        if (!CityCoreMovePreview.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        int sw = gg.guiWidth();
        int sh = gg.guiHeight();
        Matrix4f matrix = gg.pose().last().pose();

        // 收集文本行
        String title   = Component.translatable("hud.xy2407_nsuk_addition.core_move.title").getString();
        String line1   = Component.translatable("hud.xy2407_nsuk_addition.core_move.instruction1").getString();
        String line2   = Component.translatable("hud.xy2407_nsuk_addition.core_move.instruction2").getString();
        String line3   = Component.translatable("hud.xy2407_nsuk_addition.core_move.instruction3").getString();

        int titleW = textRenderer.calcWidth(mc.font, title);
        int l1w    = textRenderer.calcWidth(mc.font, line1);
        int l2w    = textRenderer.calcWidth(mc.font, line2);
        int l3w    = textRenderer.calcWidth(mc.font, line3);
        int maxW   = Math.max(titleW, Math.max(l1w, Math.max(l2w, l3w)));

        int panelW = Math.max(maxW + PAD_X * 2, PANEL_W);
        int panelH = TITLE_H + PAD_Y + LINE_H * 3 + PAD_Y;
        int px = (sw - panelW) / 2;
        int py = sh / 2 - panelH - 20; // 中心偏上

        rectRenderer.begin(gg);
        textRenderer.beginFrame(gg);
        try {
            // 背景面板
            rectRenderer.fill(matrix, px, py, panelW, panelH, BG_COLOR);
            rectRenderer.outline(matrix, px, py, panelW, panelH, BORDER_CLR);
            rectRenderer.fill(matrix, px + 1, py + 1, panelW - 2, TITLE_H - 1, TITLE_BG);
            rectRenderer.fill(matrix, px + 1, py + TITLE_H, panelW - 2, 1, BORDER_CLR);

            // 标题
            int titleX = px + (panelW - titleW) / 2;
            textRenderer.drawText(mc.font, title, titleX, py + 5, TEXT_TITLE, false, matrix);

            // 说明行
            int bodyX = px + PAD_X;
            int bodyY = py + TITLE_H + PAD_Y;

            int dotW = textRenderer.calcWidth(mc.font, "• ");
            textRenderer.drawText(mc.font, "• " + line1, bodyX, bodyY, TEXT_BODY, false, matrix);
            textRenderer.drawText(mc.font, "• " + line2, bodyX, bodyY + LINE_H, TEXT_BODY, false, matrix);
            textRenderer.drawText(mc.font, "• " + line3, bodyX, bodyY + LINE_H * 2, TEXT_BODY, false, matrix);

            // 已设置位置提示
            if (CityCoreMovePreview.getGhostPos() != null) {
                String posStr = CityCoreMovePreview.getGhostPos().toShortString();
                String posMsg = Component.translatable("hud.xy2407_nsuk_addition.core_move.pos_set", posStr).getString();
                int posW = textRenderer.calcWidth(mc.font, posMsg);
                int posX = px + (panelW - posW) / 2;
                textRenderer.drawText(mc.font, posMsg, posX, bodyY + LINE_H * 3 + 4, 0xFF66FF66, false, matrix);
            }
        } finally {
            rectRenderer.flush();
            textRenderer.endFrame();
        }
    }
}
