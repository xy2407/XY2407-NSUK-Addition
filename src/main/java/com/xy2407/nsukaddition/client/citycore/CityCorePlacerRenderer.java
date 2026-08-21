package com.xy2407.nsukaddition.client.citycore;

import client.cn.kafei.simukraft.client.buildbox.PreviewBlockData;
import client.cn.kafei.simukraft.client.buildbox.PreviewMesh;
import client.cn.kafei.simukraft.client.buildbox.PreviewMeshBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.xy2407.nsukaddition.common.citycore.CityCoreNbtLoader;
import com.xy2407.nsukaddition.common.citycore.CityCoreProjectionUtil;
import com.xy2407.nsukaddition.common.citycore.CityCoreRotationUtil;
import com.xy2407.nsukaddition.common.citycore.CityCoreStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** 城市核心图纸投影渲染：手持图纸时用 VBO 渲染 citycore 建筑虚影，按 R 旋转。 */
@OnlyIn(Dist.CLIENT)
public final class CityCorePlacerRenderer {

    private static PreviewMesh mesh;
    private static int lastRotation = -1;

    private CityCorePlacerRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!isRenderStage(event.getStage())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            releaseMesh();
            return;
        }
        ItemStack held = CityCorePlacerKeyHandler.heldPlacer(mc.player);
        if (held.isEmpty()) {
            releaseMesh();
            lastRotation = -1;
            return;
        }
        int rotation = CityCoreRotationUtil.getRotation(held);
        if (rotation != lastRotation) {
            releaseMesh();
            lastRotation = rotation;
        }
        if (mesh == null) {
            buildMesh(rotation);
            if (mesh == null || mesh.isEmpty()) {
                return;
            }
        }

        BlockPos projection = CityCoreProjectionUtil.projectionPos(mc.player);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projectionMatrix = event.getProjectionMatrix();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            drawVbo(mesh.solidBuffer(), RenderType.solid(), modelView, projectionMatrix, projection, camera, GameRenderer.getRendertypeSolidShader());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
            drawVbo(mesh.cutoutMippedBuffer(), RenderType.cutoutMipped(), modelView, projectionMatrix, projection, camera, GameRenderer.getRendertypeCutoutMippedShader());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            drawVbo(mesh.cutoutBuffer(), RenderType.cutout(), modelView, projectionMatrix, projection, camera, GameRenderer.getRendertypeCutoutShader());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            RenderSystem.depthMask(false);
            drawVbo(mesh.translucentBuffer(), RenderType.translucent(), modelView, projectionMatrix, projection, camera, GameRenderer.getRendertypeTranslucentShader());
            RenderSystem.depthMask(true);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            drawVbo(mesh.tripwireBuffer(), RenderType.tripwire(), modelView, projectionMatrix, projection, camera, GameRenderer.getRendertypeTripwireShader());
        }
        RenderSystem.enableCull();
    }

    private static boolean isRenderStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
    }

    private static void buildMesh(int rotation) {
        CityCoreStructure structure = CityCoreNbtLoader.get().rotated(rotation);
        if (structure == null || structure.isEmpty()) {
            return;
        }
        List<PreviewBlockData> previewBlocks = new ArrayList<>(structure.blocks().size());
        for (CityCoreStructure.CityCoreBlock block : structure.blocks()) {
            previewBlocks.add(new PreviewBlockData(block.pos(), block.state(), LightTexture.FULL_BLOCK, block.copyBlockEntityData()));
        }
        mesh = PreviewMeshBuilder.build(previewBlocks);
    }

    private static void releaseMesh() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
    }

    private static void drawVbo(VertexBuffer vertexBuffer, RenderType renderType, Matrix4f modelViewMatrix,
                                Matrix4f projectionMatrix, BlockPos origin, Vec3 cameraPos, ShaderInstance shader) {
        if (vertexBuffer == null) {
            return;
        }
        renderType.setupRenderState();
        RenderSystem.setShader(() -> shader);
        shader.setDefaultUniforms(renderType.mode(), modelViewMatrix, projectionMatrix, Minecraft.getInstance().getWindow());
        shader.apply();

        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set((float) (origin.getX() - cameraPos.x), (float) (origin.getY() - cameraPos.y), (float) (origin.getZ() - cameraPos.z));
            shader.CHUNK_OFFSET.upload();
        }

        vertexBuffer.bind();
        vertexBuffer.draw();
        VertexBuffer.unbind();

        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            shader.CHUNK_OFFSET.upload();
        }

        shader.clear();
        renderType.clearRenderState();
    }
}
