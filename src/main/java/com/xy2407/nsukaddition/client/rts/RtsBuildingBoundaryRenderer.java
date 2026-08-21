package com.xy2407.nsukaddition.client.rts;

import client.cn.kafei.simukraft.client.buildbox.PreviewMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * RTS 建筑放置投影 VBO 渲染（分 5 阶段绘制）。
 * 城市领地边界与已放置建筑界限线框不再在此渲染——已完全复用 simukraft 的
 * BuildingBoundsRenderer（其渲染管线已在 RTS 独立相机视角验证可正常渲染），
 * 由 BuildingBoundsRendererRtsMixin 放宽渲染条件、RtsSelectionSyncPacket 服务端
 * 经 simukraft 的 ResidentialControlBoxBoundsUpdatePacket 同步建筑界限数据。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NsukAddition.MOD_ID, value = Dist.CLIENT)
public final class RtsBuildingBoundaryRenderer {

    private RtsBuildingBoundaryRenderer() {
    }

    private static final int COLOR_VALID = 0xFF40E040;
    private static final int COLOR_INVALID = 0xFFFF4040;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!RtsModeManager.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        RenderLevelStageEvent.Stage stage = event.getStage();

        if (isMeshStage(stage)) {
            Vec3 camera = RtsModeManager.getRenderCameraPos(event.getPartialTick().getGameTimeDeltaPartialTick(true));
            if (camera == null) {
                camera = event.getCamera().getPosition();
            }
            if (RtsBuildingPlacementManager.isActive()) {
                drawMesh(meshBufferFor(RtsBuildingPlacementManager.getMesh(), stage), stage, modelView, projection,
                        renderOriginFor(RtsBuildingPlacementManager.getOrigin(), RtsBuildingPlacementManager.getMesh()), camera);
            }
            for (var pending : RtsBuildingPlacementManager.getPendingPlacements()) {
                drawMesh(meshBufferFor(pending.mesh(), stage), stage, modelView, projection,
                        renderOriginFor(new Vec3(pending.origin().getX(), pending.origin().getY(), pending.origin().getZ()),
                                pending.mesh()), camera);
            }
            if (RtsBuildingPlacementManager.isMoveActive()) {
                drawMesh(meshBufferFor(RtsBuildingPlacementManager.getMoveMesh(), stage), stage, modelView, projection,
                        renderOriginFor(RtsBuildingPlacementManager.getMoveOrigin(), RtsBuildingPlacementManager.getMoveMesh()), camera);
            }
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            Camera cam = event.getCamera();
            Vec3 camPos = cam.getPosition();
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
            VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            if (RtsBuildingPlacementManager.isActive()) {
                drawProjectionBox(poseStack, consumer, RtsBuildingPlacementManager.isValid());
            }
            if (RtsBuildingPlacementManager.isMoveActive()) {
                drawMoveProjectionBox(poseStack, consumer, RtsBuildingPlacementManager.isMoveValid());
            }
            drawPendingBoxes(poseStack, consumer);
            mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
            poseStack.popPose();
        }
    }

    private static void drawProjectionBox(PoseStack poseStack, VertexConsumer consumer, boolean valid) {
        AABB aabb = RtsBuildingPlacementManager.getPlacementAabb();
        if (aabb == null) {
            return;
        }
        renderLineBoxColor(poseStack, consumer, aabb, valid ? COLOR_VALID : COLOR_INVALID);
    }

    private static void drawMoveProjectionBox(PoseStack poseStack, VertexConsumer consumer, boolean valid) {
        AABB aabb = RtsBuildingPlacementManager.getMoveAabb();
        if (aabb == null) {
            return;
        }
        renderLineBoxColor(poseStack, consumer, aabb, valid ? COLOR_VALID : COLOR_INVALID);
    }

    private static void drawPendingBoxes(PoseStack poseStack, VertexConsumer consumer) {
        var pendings = RtsBuildingPlacementManager.getPendingPlacements();
        if (pendings.isEmpty()) {
            return;
        }
        for (var p : pendings) {
            if (p.structure() == null) {
                continue;
            }
            BlockPos originPos = p.origin();
            BlockPos maxPos = originPos.offset(p.structure().size());
            AABB box = new AABB(
                    originPos.getX(), originPos.getY(), originPos.getZ(),
                    maxPos.getX(), maxPos.getY(), maxPos.getZ());
            renderLineBoxColor(poseStack, consumer, box, COLOR_VALID);
        }
    }

    private static void renderLineBoxColor(PoseStack poseStack, VertexConsumer consumer, AABB box, int argb) {
        float red = (argb >> 16 & 0xFF) / 255.0f;
        float green = (argb >> 8 & 0xFF) / 255.0f;
        float blue = (argb & 0xFF) / 255.0f;
        float alpha = (argb >> 24 & 0xFF) / 255.0f;
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(poseStack, consumer, box, red, green, blue, alpha);
    }

    private static boolean isMeshStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
    }

    private static Vec3 renderOriginFor(Vec3 blockOrigin, PreviewMesh mesh) {
        if (mesh == null || mesh.origin() == null) {
            return blockOrigin;
        }
        net.minecraft.core.BlockPos mo = mesh.origin();
        return new Vec3(blockOrigin.x + mo.getX(), blockOrigin.y + mo.getY(), blockOrigin.z + mo.getZ());
    }

    private static VertexBuffer meshBufferFor(PreviewMesh mesh, RenderLevelStageEvent.Stage stage) {        if (mesh == null) {
            return null;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
            return mesh.cutoutMippedBuffer();
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return mesh.cutoutBuffer();
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return mesh.translucentBuffer();
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return mesh.tripwireBuffer();
        }
        return mesh.solidBuffer();
    }

    private static void drawMesh(VertexBuffer vertexBuffer, RenderLevelStageEvent.Stage stage, Matrix4f modelView,
                                 Matrix4f projection, Vec3 origin, Vec3 camera) {
        if (vertexBuffer == null || origin == null) {
            return;
        }
        RenderType renderType;
        ShaderInstance shader;
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
            renderType = RenderType.cutoutMipped();
            shader = GameRenderer.getRendertypeCutoutMippedShader();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            renderType = RenderType.cutout();
            shader = GameRenderer.getRendertypeCutoutShader();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderType = RenderType.translucent();
            shader = GameRenderer.getRendertypeTranslucentShader();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            renderType = RenderType.tripwire();
            shader = GameRenderer.getRendertypeTripwireShader();
        } else {
            renderType = RenderType.solid();
            shader = GameRenderer.getRendertypeSolidShader();
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            RenderSystem.depthMask(false);
        }
        renderType.setupRenderState();
        RenderSystem.setShader(() -> shader);
        shader.setDefaultUniforms(renderType.mode(), modelView, projection, Minecraft.getInstance().getWindow());
        shader.apply();

        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set((float) (origin.x - camera.x), (float) (origin.y - camera.y), (float) (origin.z - camera.z));
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
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            RenderSystem.depthMask(true);
        }
    }
}