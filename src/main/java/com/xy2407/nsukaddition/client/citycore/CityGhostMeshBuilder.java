package com.xy2407.nsukaddition.client.citycore;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import client.cn.kafei.simukraft.client.buildbox.PreviewBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 建筑任务虚影网格构建：顶点 alpha 固定 128 实现 50% 半透明，全部进单一缓冲。 */
@OnlyIn(Dist.CLIENT)
public final class CityGhostMeshBuilder {

    public record GhostMesh(VertexBuffer buffer, BlockPos meshOrigin) {
    }

    private CityGhostMeshBuilder() {
    }

    public static GhostMesh build(List<PreviewBlockData> allBlocks) {
        if (allBlocks == null || allBlocks.isEmpty()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        MeshLayerBuilder layer = new MeshLayerBuilder();
        BlockPos meshOrigin = findMeshOrigin(allBlocks);
        BlockAndTintGetter previewView = new PreviewBlockView(minecraft.level, allBlocks);
        ModelBlockRenderer modelRenderer = minecraft.getBlockRenderer().getModelRenderer();
        try {
            for (PreviewBlockData block : allBlocks) {
                BlockState state = block.state();
                if (state.isAir()) {
                    continue;
                }
                RenderShape renderShape = state.getRenderShape();
                boolean hasBlockEntity = state.getBlock() instanceof EntityBlock;
                if (renderShape == RenderShape.INVISIBLE && !hasBlockEntity) {
                    continue;
                }
                BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
                if (renderShape == RenderShape.ENTITYBLOCK_ANIMATED
                        || (renderShape == RenderShape.INVISIBLE && hasBlockEntity)
                        || model.isCustomRenderer()) {
                    continue;
                }
                for (RenderType renderType : model.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY)) {
                    RandomSource random = RandomSource.create();
                    PoseStack poseStack = new PoseStack();
                    poseStack.translate(block.pos().getX() - meshOrigin.getX(),
                            block.pos().getY() - meshOrigin.getY(),
                            block.pos().getZ() - meshOrigin.getZ());
                    modelRenderer.tesselateBlock(previewView, model, state, block.pos(), poseStack, layer, true,
                            random, state.getSeed(block.pos()),
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            ModelData.EMPTY, renderType);
                }
            }
            VertexBuffer buffer = layer.upload();
            if (buffer == null) {
                return null;
            }
            return new GhostMesh(buffer, meshOrigin);
        } finally {
            layer.close();
        }
    }

    private static BlockPos findMeshOrigin(List<PreviewBlockData> allBlocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (PreviewBlockData block : allBlocks) {
            BlockPos pos = block.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return BlockPos.ZERO;
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static final class PreviewBlockView implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final Map<Long, BlockState> states = new HashMap<>();

        private PreviewBlockView(BlockAndTintGetter delegate, List<PreviewBlockData> blocks) {
            this.delegate = delegate;
            for (PreviewBlockData block : blocks) {
                if (!block.state().isAir()) {
                    states.put(block.pos().asLong(), block.state());
                }
            }
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public int getBrightness(LightLayer lightLayer, BlockPos pos) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    private static final class MeshLayerBuilder implements VertexConsumer, AutoCloseable {
        private static final int CAPACITY = 512 * 1024;
        private final ByteBufferBuilder byteBuffer = new ByteBufferBuilder(CAPACITY);
        private final BufferBuilder buffer = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        private MeshLayerBuilder() {
        }

        private VertexBuffer upload() {
            MeshData mesh = buffer.build();
            if (mesh == null) {
                return null;
            }
            try {
                VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vertexBuffer.bind();
                vertexBuffer.upload(mesh);
                VertexBuffer.unbind();
                return vertexBuffer;
            } finally {
                mesh.close();
            }
        }

        @Override
        public void close() {
            byteBuffer.close();
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return buffer.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return buffer.setColor(red, green, blue, 128);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return buffer.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return buffer.setOverlay(u | v << 16);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return buffer.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return buffer.setNormal(x, y, z);
        }
    }
}
