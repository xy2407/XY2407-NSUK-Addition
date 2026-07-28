package com.xy2407.nsukaddition.client.colony;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 附属地核心迁移 ghost 方块渲染，参照 CityCoreMoveRenderer 实现带材质的半透明方块虚影。 */
@OnlyIn(Dist.CLIENT)
public final class ColonyCoreMoveRenderer {

    private static final float GHOST_ALPHA = 0.5f;

    private ColonyCoreMoveRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!ColonyCoreMovePreview.isActive()) return;

        BlockPos ghostPos = ColonyCoreMovePreview.getGhostPos();
        if (ghostPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockState state = ModBlocks.COLONY_CORE.get().defaultBlockState();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(ghostPos.getX() - cam.x, ghostPos.getY() - cam.y, ghostPos.getZ() - cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, GHOST_ALPHA);

        try {
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            mc.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY);
            bufferSource.endBatch();
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        poseStack.popPose();
    }
}
