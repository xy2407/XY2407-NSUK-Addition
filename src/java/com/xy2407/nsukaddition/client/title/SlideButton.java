package com.xy2407.nsukaddition.client.title;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 左侧弹出式按钮，悬停展开显示文字，白底黑框黑字风格。 */
@OnlyIn(Dist.CLIENT)
public final class SlideButton {

    private static final long DURATION_MS = 250L;
    private static final int COLLAPSED_RATIO = 1;

    private static final int BG_NORMAL  = 0xFFFFFFFF;
    private static final int BG_HOVER   = 0xFFF0F0F0;
    private static final int BG_PRESSED = 0xFFD0D0D0;
    private static final int BORDER     = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF000000;

    private final Component displayText;
    private final ResourceLocation iconTex;
    private final int iconW;
    private final int iconH;
    private final Runnable onClick;

    private final int fullWidth;
    private final int height;

    private float progress;
    private long lastFrameMs;
    private boolean targetExpanded;
    private boolean pressed;

    int x;
    int y;

    public SlideButton(Component text, ResourceLocation iconTex, int iconW, int iconH,
                       int fullWidth, int height, Runnable onClick) {
        this.displayText = text;
        this.iconTex = iconTex;
        this.iconW = iconW;
        this.iconH = iconH;
        this.fullWidth = fullWidth;
        this.height = height;
        this.onClick = onClick;
        this.lastFrameMs = Util.getMillis();
    }

    public int currentWidth() {
        return collapsedWidth() + Math.round((fullWidth - collapsedWidth()) * easedProgress());
    }

    public int collapsedWidth() {
        return height;
    }

    private float easedProgress() {
        float t = Math.clamp(progress, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + currentWidth()
                && mouseY >= y && mouseY <= y + height;
    }

    public void setHovered(boolean hovered) {
        this.targetExpanded = hovered;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public boolean isPressed() {
        return pressed;
    }

    public void click() {
        if (onClick != null) {
            onClick.run();
        }
    }

    public void updateAnimation() {
        long nowMs = Util.getMillis();
        float delta = (float) (nowMs - lastFrameMs) / DURATION_MS;
        lastFrameMs = nowMs;

        if (targetExpanded) {
            progress = Math.min(1f, progress + delta);
        } else {
            progress = Math.max(0f, progress - delta);
        }
    }

    public void render(GuiGraphics gg, Minecraft mc, int mouseX, int mouseY) {
        int cw = currentWidth();
        boolean hovered = isMouseOver(mouseX, mouseY);

        int bg;
        if (pressed && hovered) {
            bg = BG_PRESSED;
        } else if (hovered) {
            bg = BG_HOVER;
        } else {
            bg = BG_NORMAL;
        }

        gg.fill(x, y, x + cw, y + height, bg);

        gg.hLine(x, x + cw - 1, y, BORDER);
        gg.hLine(x, x + cw - 1, y + height - 1, BORDER);
        gg.vLine(x, y, y + height - 1, BORDER);
        gg.vLine(x + cw - 1, y, y + height - 1, BORDER);

        int padding = 4;
        int iconSize = height - padding * 2;
        if (iconSize > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int iconX = x + padding;
            int iconY = y + padding;
            gg.blit(iconTex, iconX, iconY, 0, 0f, 0f, iconSize, iconSize, iconW, iconH);
            RenderSystem.disableBlend();
        }

        if (progress > 0.4f) {
            float textAlpha = Math.min(1f, (progress - 0.4f) / 0.5f);
            int alpha = Math.clamp((int) (textAlpha * 255), 0, 255);
            int color = (alpha << 24) | TEXT_COLOR;

            int textW = mc.font.width(displayText);
            int textX = x + (cw - textW) / 2;
            int textY = y + (height - mc.font.lineHeight) / 2;
            gg.drawString(mc.font, displayText, textX, textY, color, false);
        }
    }
}
