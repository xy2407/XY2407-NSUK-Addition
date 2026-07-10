package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** 核心迁移模式 HUD：屏幕中央提示文字，告知玩家当前处于迁移模式及操作方式。 */
public final class CoreMoveHudLayer implements LayeredDraw.Layer {
    public static final CoreMoveHudLayer INSTANCE = new CoreMoveHudLayer();

    private static final int LINE_H = 14;

    private final BatchTextRenderer textRenderer = new BatchTextRenderer();

    private CoreMoveHudLayer() {}

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        if (!CityCoreMovePreview.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        int sw = gg.guiWidth();
        int sh = gg.guiHeight();
        Matrix4f matrix = gg.pose().last().pose();

        String title = Component.translatable("hud.xy2407_nsuk_addition.core_move.title").getString();
        String line1 = Component.translatable("hud.xy2407_nsuk_addition.core_move.instruction1").getString();
        String line2 = Component.translatable("hud.xy2407_nsuk_addition.core_move.instruction2").getString();
        String line3 = Component.translatable("hud.xy2407_nsuk_addition.core_move.instruction3").getString();

        int titleW = textRenderer.calcWidth(mc.font, title);
        int l1w    = textRenderer.calcWidth(mc.font, line1);
        int l2w    = textRenderer.calcWidth(mc.font, line2);
        int l3w    = textRenderer.calcWidth(mc.font, line3);

        int bodyY = sh / 2 - 40;

        textRenderer.beginFrame(gg);
        try {
            textRenderer.drawText(mc.font, title, (sw - titleW) / 2, bodyY, 0xFFFFAA00, true, matrix);
            textRenderer.drawText(mc.font, line1, (sw - l1w) / 2, bodyY + LINE_H, 0xFFFFFFFF, true, matrix);
            textRenderer.drawText(mc.font, line2, (sw - l2w) / 2, bodyY + LINE_H * 2, 0xFFFFFFFF, true, matrix);
            textRenderer.drawText(mc.font, line3, (sw - l3w) / 2, bodyY + LINE_H * 3, 0xFFFFFFFF, true, matrix);

            if (CityCoreMovePreview.getGhostPos() != null) {
                String posStr = CityCoreMovePreview.getGhostPos().toShortString();
                String posMsg = Component.translatable("hud.xy2407_nsuk_addition.core_move.pos_set", posStr).getString();
                int posW = textRenderer.calcWidth(mc.font, posMsg);
                textRenderer.drawText(mc.font, posMsg, (sw - posW) / 2, bodyY + LINE_H * 4 + 4, 0xFF66FF66, true, matrix);
            }
        } finally {
            textRenderer.endFrame();
        }
    }
}
