package com.xy2407.nsukaddition.client.rts;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** RTS 模式下为选中实体绘制发光碰撞箱轮廓（绿色线框）和右键移动目标标记（黄色旗帜 + 青色连线）。 */
@OnlyIn(Dist.CLIENT)
public final class RtsEntityGlowRenderer {

    private static final float MARK_R = 1.0F;
    private static final float MARK_G = 0.85F;
    private static final float MARK_B = 0.0F;
    private static final float LINE_R = 0.3F;
    private static final float LINE_G = 0.8F;
    private static final float LINE_B = 1.0F;
    private static final double MARK_HALF = 0.35D;
    private static final double MARK_PILLAR = 1.5D;

    private RtsEntityGlowRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!RtsModeManager.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        RtsModeManager.setCachedProjectionMatrix(RenderSystem.getProjectionMatrix());

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        Set<UUID> selected = RtsModeManager.getSelectedEntities();
        if (!selected.isEmpty()) {
            AABB searchBox = new AABB(
                    camPos.x - 200.0, camPos.y - 200.0, camPos.z - 200.0,
                    camPos.x + 200.0, camPos.y + 200.0, camPos.z + 200.0);
            List<Entity> selectedEntities = new ArrayList<>();
            for (Entity entity : mc.level.getEntities(mc.player, searchBox)) {
                if (!selected.contains(entity.getUUID())) continue;
                if (!entity.isAlive()) continue;
                selectedEntities.add(entity);
                renderBox(poseStack, consumer, entity, partialTick);
            }
            if (selected.contains(mc.player.getUUID()) && mc.player.isAlive()) {
                selectedEntities.add(mc.player);
                renderBox(poseStack, consumer, mc.player, partialTick);
            }

            Map<UUID, Vec3> moveTargets = RtsModeManager.getMoveTargets();
            if (!moveTargets.isEmpty()) {
                Set<Vec3> drawnMarkers = new HashSet<>();
                for (Vec3 target : moveTargets.values()) {
                    if (!drawnMarkers.add(target)) continue;
                    double markY = target.y + 0.05D;
                    renderMoveTarget(poseStack, consumer, target, markY);
                }
                for (Map.Entry<UUID, Vec3> entry : moveTargets.entrySet()) {
                    Entity entity = findEntity(mc.level, entry.getKey());
                    if (entity == null || !entity.isAlive()) continue;
                    Vec3 target = entry.getValue();
                    double markY = target.y + 0.05D;
                    Vec3 markTop = new Vec3(target.x, markY + MARK_PILLAR, target.z);
                    renderLine(poseStack, consumer, interpolatedPos(entity, partialTick), markTop);
                }
            }
        }

        Map<UUID, Set<UUID>> attackTargets = RtsModeManager.getAttackTargets();
        if (!attackTargets.isEmpty()) {
            Set<UUID> drawnTargets = new HashSet<>();
            for (Map.Entry<UUID, Set<UUID>> entry : attackTargets.entrySet()) {
                Entity npcEntity = findEntity(mc.level, entry.getKey());
                if (npcEntity == null || !npcEntity.isAlive()) continue;
                for (UUID targetId : entry.getValue()) {
                    if (!drawnTargets.add(targetId)) continue;
                    Entity targetEntity = findEntity(mc.level, targetId);
                    if (targetEntity == null || !targetEntity.isAlive()) continue;
                    renderAttackTarget(poseStack, consumer, targetEntity, partialTick);
                }
            }
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());

        renderNpcHealthLabels(poseStack, mc, camPos, partialTick);
        mc.renderBuffers().bufferSource().endLastBatch();

        poseStack.popPose();
    }

    private static void renderNpcHealthLabels(PoseStack poseStack, Minecraft mc, Vec3 camPos, float partialTick) {
        AABB searchBox = new AABB(
                camPos.x - 200.0, camPos.y - 200.0, camPos.z - 200.0,
                camPos.x + 200.0, camPos.y + 200.0, camPos.z + 200.0);
        for (Entity e : mc.level.getEntities(mc.player, searchBox)) {
            if (!(e instanceof net.minecraft.world.entity.LivingEntity npc) || !npc.isAlive()) continue;

            float hp = npc.getHealth();
            float hearts = Math.max(0.0F, hp / 2.0F);
            String text = "\u2764" + String.format("%.1f", hearts);
            int width = mc.font.width(text);

            Vec3 pos = interpolatedPos(npc, partialTick);
            poseStack.pushPose();
            poseStack.translate(pos.x, pos.y + npc.getBbHeight() + 0.35D, pos.z);
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poseStack.last().pose();
            float g = -width / 2.0F;
            mc.font.drawInBatch(text, g, 0.0F, 0xFFFF5555, false, matrix,
                    mc.renderBuffers().bufferSource(), net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                    0x80000000, 0x00F000F0);
            poseStack.popPose();
        }
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, Entity entity, float partialTick) {
        Vec3 pos = interpolatedPos(entity, partialTick);
        AABB box = entity.getBoundingBox()
                .move(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ())
                .inflate(0.05);
        if (entity instanceof net.minecraft.world.entity.player.Player || entity instanceof RtsFakePlayerEntity) {
            LevelRenderer.renderLineBox(poseStack, consumer, box, 0.3F, 0.6F, 1.0F, 1.0F);
        } else {
            LevelRenderer.renderLineBox(poseStack, consumer, box, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private static void renderAttackTarget(PoseStack poseStack, VertexConsumer consumer, Entity entity, float partialTick) {
        Vec3 pos = interpolatedPos(entity, partialTick);
        AABB box = entity.getBoundingBox()
                .move(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ())
                .inflate(0.15);
        LevelRenderer.renderLineBox(poseStack, consumer, box, 1.0F, 0.15F, 0.15F, 1.0F);
    }

    private static Entity findEntity(net.minecraft.client.multiplayer.ClientLevel level, UUID id) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) return entity;
        }
        return null;
    }

    private static Vec3 interpolatedPos(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ())
        );
    }

    private static void renderMoveTarget(PoseStack poseStack, VertexConsumer consumer, Vec3 target, double markY) {
        AABB baseBox = new AABB(
                target.x - MARK_HALF, markY - 0.05D, target.z - MARK_HALF,
                target.x + MARK_HALF, markY + 0.05D, target.z + MARK_HALF);
        LevelRenderer.renderLineBox(poseStack, consumer, baseBox, MARK_R, MARK_G, MARK_B, 1.0F);
        AABB pillarBox = new AABB(
                target.x - 0.06D, markY, target.z - 0.06D,
                target.x + 0.06D, markY + MARK_PILLAR, target.z + 0.06D);
        LevelRenderer.renderLineBox(poseStack, consumer, pillarBox, MARK_R, MARK_G, MARK_B, 1.0F);
        AABB flagBox = new AABB(
                target.x - MARK_HALF, markY + MARK_PILLAR - 0.1D, target.z - 0.06D,
                target.x + MARK_HALF, markY + MARK_PILLAR, target.z + 0.06D);
        LevelRenderer.renderLineBox(poseStack, consumer, flagBox, MARK_R, MARK_G, MARK_B, 1.0F);
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        Matrix4f matrix = poseStack.last().pose();
        float dx = (float) (to.x - from.x);
        float dy = (float) (to.y - from.y);
        float dz = (float) (to.z - from.z);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len > 0 ? dx / len : 0.0F;
        float ny = len > 0 ? dy / len : 0.0F;
        float nz = len > 0 ? dz / len : 0.0F;
        consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(LINE_R, LINE_G, LINE_B, 1.0F)
                .setNormal(nx, ny, nz);
        consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(LINE_R, LINE_G, LINE_B, 1.0F)
                .setNormal(nx, ny, nz);
    }
}