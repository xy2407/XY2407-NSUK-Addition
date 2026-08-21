package com.xy2407.nsukaddition.client.city;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityCorePositionsCache;
import com.xy2407.nsukaddition.common.network.city.CityCorePositionsPacket.CoreInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/** 城市核心方块发光轮廓渲染器：自家绿色、别家红色，关闭深度测试实现透视方块。 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NsukAddition.MOD_ID, value = Dist.CLIENT)
public final class CityCoreGlowRenderer {

    private static final float OWN_R = 0.0F, OWN_G = 1.0F, OWN_B = 0.0F;
    private static final float OTHER_R = 1.0F, OTHER_G = 0.0F, OTHER_B = 0.0F;
    private static final float ALPHA = 0.85F;
    private static final float EXPAND = 0.03F;
    private static final double MAX_RENDER_DISTANCE = 64.0D;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    private CityCoreGlowRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<CoreInfo> cores = CityCorePositionsCache.getCores();
        if (cores.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        double camX = camera.x;
        double camY = camera.y;
        double camZ = camera.z;

        boolean hasAny = false;
        for (CoreInfo info : cores) {
            BlockPos pos = info.pos();
            double dx = pos.getX() + 0.5D - camX;
            double dy = pos.getY() + 0.5D - camY;
            double dz = pos.getZ() + 0.5D - camZ;
            if (dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQ) {
                hasAny = true;
                break;
            }
        }
        if (!hasAny) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (CoreInfo info : cores) {
            BlockPos pos = info.pos();
            double dx = pos.getX() + 0.5D - camX;
            double dy = pos.getY() + 0.5D - camY;
            double dz = pos.getZ() + 0.5D - camZ;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) continue;

            float r = info.mine() ? OWN_R : OTHER_R;
            float g = info.mine() ? OWN_G : OTHER_G;
            float b = info.mine() ? OWN_B : OTHER_B;

            double minX = pos.getX() - camX - EXPAND;
            double minY = pos.getY() - camY - EXPAND;
            double minZ = pos.getZ() - camZ - EXPAND;
            double maxX = pos.getX() - camX + 1 + EXPAND;
            double maxY = pos.getY() - camY + 1 + EXPAND;
            double maxZ = pos.getZ() - camZ + 1 + EXPAND;

            drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, ALPHA);
            drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, ALPHA);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawLine(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(red, green, blue, alpha);
    }
}
