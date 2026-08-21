package com.xy2407.nsukaddition.client.citycore;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import client.cn.kafei.simukraft.client.buildbox.PreviewBlockData;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 城市建筑任务虚影渲染：进行中/排队任务以 50% 半透明投影常驻显示，每5秒同步一次。 */
@OnlyIn(Dist.CLIENT)
public final class CityGhostRenderer {
    private static final int SYNC_INTERVAL = 100;
    private static final ConcurrentMap<UUID, GhostEntry> PROJECTIONS = new ConcurrentHashMap<>();
    private static final GhostEntry ABSENT = new GhostEntry(null, null, "");

    public record GhostEntry(VertexBuffer buffer, BlockPos meshOrigin, String dimensionId) {
    }

    private CityGhostRenderer() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            releaseAll();
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (mc.level.getGameTime() % SYNC_INTERVAL != 0L) {
            return;
        }
        PacketDistributor.sendToServer(new com.xy2407.nsukaddition.common.network.citycore.CityGhostRequestPacket());
    }

    public static void onLogout() {
        releaseAll();
    }

    public static void applySnapshot(List<com.xy2407.nsukaddition.common.network.citycore.CityGhostSyncPacket.GhostTaskInfo> infos) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Set<UUID> keep = new HashSet<>();
        for (var info : infos) {
            keep.add(info.taskId());
            PROJECTIONS.computeIfAbsent(info.taskId(), id -> {
                GhostEntry entry = buildEntry(info);
                return entry != null ? entry : ABSENT;
            });
        }
        PROJECTIONS.keySet().removeIf(id -> {
            if (keep.contains(id)) {
                return false;
            }
            GhostEntry entry = PROJECTIONS.remove(id);
            if (entry != null && entry.buffer() != null) {
                entry.buffer().close();
            }
            return true;
        });
    }

    private static GhostEntry buildEntry(com.xy2407.nsukaddition.common.network.citycore.CityGhostSyncPacket.GhostTaskInfo info) {
        Optional<BuildingStructure> structure = BuildingStructureService.loadStructure(info.category(), info.buildingFileName());
        if (structure.isEmpty()) {
            return null;
        }
        List<BuildingBlockData> placed = BuildingStructureService.resolvePlacedBlocks(structure.get(), info.origin(), info.rotationDegrees());
        List<PreviewBlockData> preview = new ArrayList<>();
        for (BuildingBlockData b : placed) {
            if (!b.state().isAir()) {
                preview.add(new PreviewBlockData(b.relativePos(), b.state(), LightTexture.FULL_BRIGHT));
            }
        }
        CityGhostMeshBuilder.GhostMesh mesh = CityGhostMeshBuilder.build(preview);
        if (mesh == null) {
            return null;
        }
        return new GhostEntry(mesh.buffer(), mesh.meshOrigin(), info.dimensionId());
    }

    private static void releaseAll() {
        for (GhostEntry entry : PROJECTIONS.values()) {
            if (entry != null && entry.buffer() != null) {
                entry.buffer().close();
            }
        }
        PROJECTIONS.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PROJECTIONS.isEmpty()) {
            return;
        }
        String dimension = mc.level.dimension().location().toString();
        Matrix4f projection = event.getProjectionMatrix();
        Matrix4f modelView = event.getModelViewMatrix();
        Vec3 cameraPos = event.getCamera().getPosition();
        RenderType renderType = RenderType.translucent();
        ShaderInstance shader = GameRenderer.getRendertypeTranslucentShader();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        for (GhostEntry entry : PROJECTIONS.values()) {
            if (!dimension.equals(entry.dimensionId())) {
                continue;
            }
            drawVbo(entry.buffer(), renderType, modelView, projection, entry.meshOrigin(), cameraPos, shader);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void drawVbo(VertexBuffer vertexBuffer, RenderType renderType, Matrix4f modelViewMatrix,
                                Matrix4f projection, BlockPos origin, Vec3 cameraPos, ShaderInstance shader) {
        if (vertexBuffer == null) {
            return;
        }
        renderType.setupRenderState();
        RenderSystem.setShader(() -> shader);
        shader.setDefaultUniforms(renderType.mode(), modelViewMatrix, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set((float) (origin.getX() - cameraPos.x),
                    (float) (origin.getY() - cameraPos.y),
                    (float) (origin.getZ() - cameraPos.z));
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
