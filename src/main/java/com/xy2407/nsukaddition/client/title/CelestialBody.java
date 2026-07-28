package com.xy2407.nsukaddition.client.title;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/** 标题屏幕天体（太阳/月亮），原版贴图 + GPU 径向光晕，沿弧线运动，支持拖拽。 */
@OnlyIn(Dist.CLIENT)
public final class CelestialBody {

    public enum Type { SUN, MOON }

    private static final ResourceLocation SUN_TEX =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/sun.png");
    private static final ResourceLocation MOON_TEX =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/moon_phases.png");

    private static final float CYCLE_SEC = 35f;
    private static final float FADE_ZONE = 0.06f;
    private static final int GLOW_SEGMENTS = 48;

    private Type type = Type.SUN;
    private float progress;
    private long lastMs;

    private boolean dragging;
    private boolean returning;
    private float dragStartProgress;
    private float bodyX, bodyY;
    private float dragOffX, dragOffY;

    private float returnStartX, returnStartY;
    private float returnTargetX, returnTargetY;
    private float returnElapsed;
    private float returnDuration;

    public CelestialBody() {
        lastMs = Util.getMillis();
    }

    public void update() {
        long now = Util.getMillis();
        float dt = (float) (now - lastMs) / 1000f;
        lastMs = now;

        if (dragging) return;

        if (returning) {
            returnElapsed += dt;
            float t = Math.min(1f, returnElapsed / returnDuration);
            if (t > 0.85f) {
                float tail = (t - 0.85f) / 0.15f;
                float eased = 1f - (1f - tail) * (1f - tail);
                t = 0.85f + eased * 0.15f;
            }
            bodyX = returnStartX + (returnTargetX - returnStartX) * t;
            bodyY = returnStartY + (returnTargetY - returnStartY) * t;
            if (t >= 1f) {
                returning = false;
                progress = dragStartProgress;
            }
            return;
        }

        progress += dt / CYCLE_SEC;
        if (progress >= 1f) {
            progress -= 1f;
            type = (type == Type.SUN) ? Type.MOON : Type.SUN;
        }
    }

    public Type type() { return type; }

    private static float[] arcPos(float t, int w, int h) {
        float angle = (float) (Math.PI * (1f - t));
        float x = w / 2f + (w / 2f) * (float) Math.cos(angle);
        float y = h / 2f - (h * 0.35f) * (float) Math.sin(angle);
        return new float[]{x, y};
    }

    private static int size(int h) {
        return Math.max(24, h / 18) * 4;
    }

    public boolean isMouseOver(int mx, int my, int w, int h) {
        int s = size(h);
        float dx = mx - bodyX, dy = my - bodyY;
        return dx * dx + dy * dy <= (s / 2f + 10) * (s / 2f + 10);
    }

    public void startDrag(int mx, int my) {
        dragging = true;
        dragStartProgress = progress;
        dragOffX = bodyX - mx;
        dragOffY = bodyY - my;
    }

    public void dragTo(int mx, int my) {
        if (!dragging) return;
        bodyX = mx + dragOffX;
        bodyY = my + dragOffY;
    }

    public void endDrag(int w, int h) {
        if (!dragging) return;
        dragging = false;

        float[] target = arcPos(dragStartProgress, w, h);
        returnTargetX = target[0];
        returnTargetY = target[1];
        returnStartX = bodyX;
        returnStartY = bodyY;

        float dx = returnTargetX - returnStartX;
        float dy = returnTargetY - returnStartY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float arcSpeed = (float) (Math.PI * w / 2f) / CYCLE_SEC;

        if (dist < 1f || arcSpeed < 0.1f) {
            returning = false;
            progress = dragStartProgress;
            bodyX = returnTargetX;
            bodyY = returnTargetY;
        } else {
            returning = true;
            returnElapsed = 0f;
            returnDuration = dist / arcSpeed;
        }
    }

    public boolean isDragging() { return dragging; }

    public void render(GuiGraphics gg, int w, int h) {
        if (!dragging && !returning) {
            float[] pos = arcPos(progress, w, h);
            bodyX = pos[0];
            bodyY = pos[1];
        }

        float alpha = 1f;
        if (!dragging && !returning) {
            if (progress < FADE_ZONE) alpha = progress / FADE_ZONE;
            else if (progress > 1f - FADE_ZONE) alpha = (1f - progress) / FADE_ZONE;
        }
        alpha = Math.clamp(alpha, 0f, 1f);
        if (alpha <= 0f) return;

        int s = size(h);

        renderGlow(gg, bodyX, bodyY, s, type, alpha);

        renderBody(gg, s, alpha);
    }

    private void renderBody(GuiGraphics gg, int bodySize, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        ResourceLocation tex = (type == Type.SUN) ? SUN_TEX : MOON_TEX;
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        float u0, u1, v0, v1;
        if (type == Type.SUN) {
            u0 = 0f; u1 = 1f; v0 = 0f; v1 = 1f;
        } else {
            u0 = 0f; u1 = 0.25f; v0 = 0f; v1 = 0.5f;
        }

        Matrix4f matrix = gg.pose().last().pose();
        float x0 = bodyX - bodySize / 2f;
        float y0 = bodyY - bodySize / 2f;
        float x1 = bodyX + bodySize / 2f;
        float y1 = bodyY + bodySize / 2f;

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        buffer.addVertex(matrix, x0, y1, 0f).setUv(u0, v1);
        buffer.addVertex(matrix, x1, y1, 0f).setUv(u1, v1);
        buffer.addVertex(matrix, x1, y0, 0f).setUv(u1, v0);
        buffer.addVertex(matrix, x0, y0, 0f).setUv(u0, v0);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void renderGlow(GuiGraphics gg, float cx, float cy, int bodySize, Type type, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = gg.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        float r, g, b;
        if (type == Type.SUN) {
            r = 1.0f; g = 0.85f; b = 0.4f;
        } else {
            r = 0.7f; g = 0.8f; b = 1.0f;
        }

        float innerAlpha = alpha * 0.5f;
        float midAlpha = alpha * 0.25f;
        float outerAlpha = alpha * 0.1f;

        addGlowLayer(buffer, matrix, cx, cy, bodySize * 1.3f, r, g, b, innerAlpha, GLOW_SEGMENTS);
        addGlowLayer(buffer, matrix, cx, cy, bodySize * 1.8f, r, g, b, midAlpha, GLOW_SEGMENTS);
        addGlowLayer(buffer, matrix, cx, cy, bodySize * 2.2f, r, g, b, outerAlpha, GLOW_SEGMENTS);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.disableBlend();
    }

    private static void addGlowLayer(BufferBuilder buffer, Matrix4f matrix,
                                     float cx, float cy, float radius,
                                     float r, float g, float b, float centerAlpha,
                                     int segments) {
        buffer.addVertex(matrix, cx, cy, 0f).setColor(r, g, b, centerAlpha);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float x = cx + radius * (float) Math.cos(angle);
            float y = cy + radius * (float) Math.sin(angle);
            buffer.addVertex(matrix, x, y, 0f).setColor(r, g, b, 0f);
        }
    }
}
