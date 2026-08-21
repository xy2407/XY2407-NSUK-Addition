package com.xy2407.nsukaddition.client.rts;

import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.common.network.rts.RtsAttackTargetClearPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsFakePlayerSpawnPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsJadeFocusPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsPlayerTeleportPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsSelectionSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** RTS 灵魂出窍视角：相机完全独立飞行（位置+旋转），玩家实体静止，鼠标自由框选/点选控制单位。 */
@OnlyIn(Dist.CLIENT)
public final class RtsModeManager {

    private static boolean active = false;

    private static Vec3 cameraPos = null;

    private static float cameraYaw = 0.0F;

    private static float cameraPitch = 30.0F;

    private static Vec3 prevCameraPos = null;

    private static float prevCameraYaw = 0.0F;

    private static float prevCameraPitch = 30.0F;

    private static final Set<UUID> selectedEntities = new HashSet<>();

    private static boolean leftMouseDown = false;

    private static boolean boxSelecting = false;

    private static double boxStartX = 0.0;
    private static double boxStartY = 0.0;

    private static double boxEndX = 0.0;
    private static double boxEndY = 0.0;

    private static final double BOX_SELECT_THRESHOLD = 5.0;

    private static final double CAMERA_MOVE_SPEED_FAST = 1.8;
    private static final double CAMERA_MOVE_SPEED_SLOW = 0.45;

    private static final double ENTER_HEIGHT_OFFSET = 12.0;
    private static final double ZOOM_STEP = 1.0;
    private static final double ZOOM_SMOOTHING = 0.35;
    private static double zoomTargetY;

    public enum RtsViewMode { FREE, ISO_60, ISO_45 }

    private static RtsViewMode viewMode = RtsViewMode.FREE;

    private static boolean orthoEnabled = false;

    private static double orthoHalfWidth = 0.0;

    public static boolean isOrthoEnabled() {
        return orthoEnabled;
    }

    public static void setOrthoEnabled(boolean enable) {
        orthoEnabled = enable;
        if (enable) {
            updateOrthoHalfWidth();
        }
    }

    public static double getOrthoHalfWidth() {
        return orthoHalfWidth;
    }

    public static void updateOrthoHalfWidth() {
        Minecraft mc = Minecraft.getInstance();
        double height = cameraPos == null ? ENTER_HEIGHT_OFFSET : Math.max(cameraPos.y - mc.player.getY(), 8.0);
        double pitchRad = Math.toRadians(Math.max(Math.abs(cameraPitch), 30.0));
        double factor = 1.0 / Math.tan(pitchRad) * 0.9;
        orthoHalfWidth = Math.max(16.0, height * factor);
    }

    private static boolean middleMouseDown = false;

    private static double middleDragLastX = 0.0;
    private static double middleDragLastY = 0.0;

    private static Matrix4f cachedProjectionMatrix = null;

    private static final Map<UUID, Vec3> moveTargets = new HashMap<>();

    private static final Map<UUID, Set<UUID>> attackTargets = new HashMap<>();

    private static boolean selectionLocked = false;

    public enum RtsFormation { NONE, LINE, SQUARE, TRIANGLE }

    private static RtsFormation formation = RtsFormation.NONE;

    public static RtsFormation getFormation() {
        return formation;
    }

    public static void setFormation(RtsFormation f) {
        formation = f != null ? f : RtsFormation.NONE;
    }

    private static boolean ctrlHeld = false;

    private static boolean ctrlBoxSelecting = false;

    private RtsModeManager() {
    }

    public static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        cameraPos = mc.player.position().add(0.0, ENTER_HEIGHT_OFFSET, 0.0);
        cameraYaw = mc.player.getYRot();
        cameraPitch = 30.0F;
        viewMode = RtsViewMode.FREE;
        zoomTargetY = cameraPos.y;
        prevCameraPos = cameraPos;
        prevCameraYaw = cameraYaw;
        prevCameraPitch = cameraPitch;
        active = true;
        RtsBuildingPlacementManager.deactivate();
        RtsBuildingPlacementManager.endMove();
        selectedEntities.clear();
        moveTargets.clear();
        attackTargets.clear();
        selectionLocked = false;
        syncSelectionToServer();
        boxSelecting = false;
        ctrlBoxSelecting = false;
        leftMouseDown = false;
        middleMouseDown = false;
        mc.mouseHandler.releaseMouse();
        mc.gameRenderer.setRenderHand(false);
        PacketDistributor.sendToServer(new RtsFakePlayerSpawnPacket());
    }

    public static void exit() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        orthoEnabled = false;
        cameraPos = null;
        prevCameraPos = null;
        selectedEntities.clear();
        moveTargets.clear();
        RtsBuildingPlacementManager.deactivate();
        RtsBuildingPlacementManager.endMove();
        RtsBuildingPlacementManager.clearPending();
        attackTargets.clear();
        selectionLocked = false;
        syncSelectionToServer();
        boxSelecting = false;
        ctrlBoxSelecting = false;
        leftMouseDown = false;
        middleMouseDown = false;
        if (mc.player != null) {
            Vec3 teleportPos = mc.player.position();
            if (mc.level != null) {
                for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(500))) {
                    if (e instanceof RtsFakePlayerEntity fake && fake.getOwnerUUID().equals(mc.player.getUUID())) {
                        teleportPos = fake.position();
                        break;
                    }
                }
            }
            PacketDistributor.sendToServer(new RtsPlayerTeleportPacket(teleportPos));
        }
        PacketDistributor.sendToServer(new RtsJadeFocusPacket(new UUID(0L, 0L)));
        RtsPicker.lastFocusEntityId = -1;
        mc.gameRenderer.setRenderHand(true);
        if (mc.player != null && mc.screen == null) {
            mc.mouseHandler.grabMouse();
        }
    }

    public static void toggle() {
        if (active) exit();
        else enter();
    }

    public static void onPlayerDeath() {
        if (!active) return;
        selectedEntities.clear();
        moveTargets.clear();
        attackTargets.clear();
        selectionLocked = false;
        syncSelectionToServer();
        boxSelecting = false;
        ctrlBoxSelecting = false;
        leftMouseDown = false;
        middleMouseDown = false;
    }

    public static void syncCameraToRespawn() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        cameraPos = mc.player.position().add(0.0, ENTER_HEIGHT_OFFSET, 0.0);
        cameraYaw = mc.player.getYRot();
        cameraPitch = 30.0F;
        viewMode = RtsViewMode.FREE;
        zoomTargetY = cameraPos.y;
        prevCameraPos = cameraPos;
        prevCameraYaw = cameraYaw;
        prevCameraPitch = cameraPitch;
        PacketDistributor.sendToServer(new RtsFakePlayerSpawnPacket());
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 getCameraPos() {
        return cameraPos;
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static boolean isIso45() {
        return viewMode != RtsViewMode.FREE;
    }

    public static RtsViewMode getViewMode() {
        return viewMode;
    }

    public static void setViewMode(RtsViewMode mode) {
        viewMode = mode;
        if (mode == RtsViewMode.ISO_60) {
            cameraYaw = 45.0F;
            cameraPitch = 60.0F;
        } else if (mode == RtsViewMode.ISO_45) {
            cameraYaw = 45.0F;
            cameraPitch = 45.0F;
        }
    }

    public static Vec3 getRenderCameraPos(float partialTicks) {
        if (cameraPos == null) return null;
        if (prevCameraPos == null) return cameraPos;
        return new Vec3(
                prevCameraPos.x + (cameraPos.x - prevCameraPos.x) * partialTicks,
                prevCameraPos.y + (cameraPos.y - prevCameraPos.y) * partialTicks,
                prevCameraPos.z + (cameraPos.z - prevCameraPos.z) * partialTicks
        );
    }

    public static float getRenderCameraYaw(float partialTicks) {
        return lerpAngle(prevCameraYaw, cameraYaw, partialTicks);
    }

    public static float getRenderCameraPitch(float partialTicks) {
        return prevCameraPitch + (cameraPitch - prevCameraPitch) * partialTicks;
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        return from + diff * t;
    }

    public static Set<UUID> getSelectedEntities() {
        return selectedEntities;
    }

    public static boolean isBoxSelecting() {
        return boxSelecting;
    }

    public static boolean isLeftMouseDown() {
        return leftMouseDown;
    }

    public static boolean isMiddleMouseDown() {
        return middleMouseDown;
    }

    public static double getBoxStartX() {
        return boxStartX;
    }

    public static double getBoxStartY() {
        return boxStartY;
    }

    public static double getBoxEndX() {
        return boxEndX;
    }

    public static double getBoxEndY() {
        return boxEndY;
    }

    public static double[] getGuiScaledMouse() {
        Minecraft mc = Minecraft.getInstance();
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        double scale = mc.getWindow().getGuiScale();
        return new double[]{x / scale, y / scale};
    }

    public static void setCachedProjectionMatrix(Matrix4f matrix) {
        cachedProjectionMatrix = new Matrix4f(matrix);
    }

    public static Matrix4f getCachedProjectionMatrix() {
        return cachedProjectionMatrix;
    }

    public static void setMoveTargets(Set<UUID> ids, Vec3 target) {
        moveTargets.clear();
        if (ids == null || target == null) return;
        for (UUID id : ids) {
            moveTargets.put(id, target);
        }
    }

    public static Map<UUID, Vec3> getMoveTargets() {
        return moveTargets;
    }

    public static void setAttackTarget(Set<UUID> npcIds, UUID targetId) {
        if (npcIds == null || targetId == null) return;
        for (UUID id : npcIds) {
            attackTargets.computeIfAbsent(id, k -> new HashSet<>()).add(targetId);
        }
    }

    public static void setAttackTargets(Map<UUID, Set<UUID>> assignments) {
        if (assignments == null) return;
        for (Map.Entry<UUID, Set<UUID>> e : assignments.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            attackTargets.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
        }
    }

    public static Map<UUID, Set<UUID>> getAttackTargets() {
        return attackTargets;
    }

    public static boolean isSelectionLocked() {
        return selectionLocked;
    }

    public static void lockSelection() {
        if (!selectedEntities.isEmpty()) {
            selectionLocked = true;
        }
    }

    public static void unlockSelection() {
        selectionLocked = false;
    }

    public static boolean isCtrlHeld() {
        return ctrlHeld;
    }

    public static void setCtrlHeld(boolean held) {
        ctrlHeld = held;
    }

    public static boolean isCtrlBoxSelecting() {
        return ctrlBoxSelecting;
    }

    public static void clearAllAttackTargets() {
        attackTargets.clear();
        selectionLocked = false;
        PacketDistributor.sendToServer(new RtsAttackTargetClearPacket());
    }

    public static void onLeftMouseDown() {
        leftMouseDown = true;
        ctrlBoxSelecting = ctrlHeld;
        double[] pos = getGuiScaledMouse();
        boxStartX = pos[0];
        boxStartY = pos[1];
        boxEndX = pos[0];
        boxEndY = pos[1];
        boxSelecting = false;
    }

    public static void onLeftMouseDrag() {
        if (!leftMouseDown) return;
        double[] pos = getGuiScaledMouse();
        boxEndX = pos[0];
        boxEndY = pos[1];
        double dx = pos[0] - boxStartX;
        double dy = pos[1] - boxStartY;
        if (dx * dx + dy * dy > BOX_SELECT_THRESHOLD * BOX_SELECT_THRESHOLD) {
            boxSelecting = true;
        }
    }

    public static void onLeftMouseUp() {
        if (!leftMouseDown) return;
        leftMouseDown = false;
        if (boxSelecting) {
            if (ctrlBoxSelecting) {
                RtsPicker.boxSelectAttackTargets(boxStartX, boxStartY, boxEndX, boxEndY);
            } else {
                RtsPicker.boxSelect(boxStartX, boxStartY, boxEndX, boxEndY);
            }
            boxSelecting = false;
            ctrlBoxSelecting = false;
        } else {
            double[] pos = getGuiScaledMouse();
            RtsPicker.clickSelect(pos[0], pos[1]);
        }
    }

    public static void cancelLeftClick() {
        leftMouseDown = false;
        boxSelecting = false;
        ctrlBoxSelecting = false;
        boxStartX = boxStartY = boxEndX = boxEndY = 0.0D;
    }

    public static void onMiddleDown() {
        middleMouseDown = true;
        double[] pos = getGuiScaledMouse();
        middleDragLastX = pos[0];
        middleDragLastY = pos[1];
    }

    public static void onMiddleUp() {
        middleMouseDown = false;
    }

    public static void onMiddleDrag() {
        if (!middleMouseDown) return;
        double[] pos = getGuiScaledMouse();
        double dx = pos[0] - middleDragLastX;
        double dy = pos[1] - middleDragLastY;
        middleDragLastX = pos[0];
        middleDragLastY = pos[1];

        float yawDelta = (float) (dx * 0.5);
        if (viewMode != RtsViewMode.FREE) {
            cameraYaw += yawDelta;
            prevCameraYaw += yawDelta;
            return;
        }
        float newPitch = (float) Math.max(-89.0, Math.min(89.0, cameraPitch + dy * 0.5));
        float pitchDelta = newPitch - cameraPitch;

        cameraYaw += yawDelta;
        cameraPitch = newPitch;

        prevCameraYaw += yawDelta;
        prevCameraPitch += pitchDelta;
    }

    public static void onScroll(double vertical) {
        if (!active) return;
        if (RtsBuildingPlacementManager.isActive() && isLeftMouseDown()) {
            RtsBuildingPlacementManager.adjustHeight(vertical > 0.0D ? 1 : -1);
            return;
        }
        if (isLeftMouseDown() && RtsBuildingPlacementManager.hasPendingPlacements()) {
            if (RtsBuildingPlacementManager.adjustPendingHeight(vertical > 0.0D ? 1 : -1)) {
                return;
            }
        }
        if (RtsBuildingPlacementManager.isMoveActive()) {
            RtsBuildingPlacementManager.adjustMoveHeight(vertical > 0.0D ? 1 : -1);
            return;
        }
        if (RtsBuildingListHudLayer.isListActive()) {
            RtsBuildingListHudLayer.scrollBy(vertical);
            return;
        }
        if (RtsBuildingPlacementManager.isActive()) {
            RtsBuildingPlacementManager.adjustHeight(vertical > 0.0D ? 1 : -1);
            return;
        }
        if (cameraPos != null) {
            int step = (int) Math.max(-5, Math.min(5, vertical));
            zoomTargetY = Math.min(256.0D, zoomTargetY + step);
        }
    }

    public static void setSelectedEntities(Set<UUID> ids) {
        selectedEntities.clear();
        if (ids != null) selectedEntities.addAll(ids);
        RtsPlacedBuildingCache.onSelectionChanged(selectedEntities);
        syncSelectionToServer();
    }

    public static void clearSelection() {
        if (!selectedEntities.isEmpty()) {
            selectedEntities.clear();
            moveTargets.clear();
            RtsPlacedBuildingCache.onSelectionChanged(selectedEntities);
            syncSelectionToServer();
        }
    }

    private static void syncSelectionToServer() {
        PacketDistributor.sendToServer(new RtsSelectionSyncPacket(new HashSet<>(selectedEntities)));
    }

    public static void tickCamera() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cameraPos == null) return;

        if (mc.screen != null) return;
        if (RtsBuildingListHudLayer.isBlockingCameraKeys()) return;

        prevCameraPos = cameraPos;
        prevCameraYaw = cameraYaw;
        prevCameraPitch = cameraPitch;

        long window = mc.getWindow().getWindow();
        boolean keyW = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean keyS = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean keyA = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean keyD = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        boolean keySpace = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean keyShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

        float yawRad = (float) Math.toRadians(cameraYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = -Math.cos(yawRad);
        double strafeZ = -Math.sin(yawRad);

        double moveX = 0.0;
        double moveZ = 0.0;
        if (keyW) { moveX += forwardX; moveZ += forwardZ; }
        if (keyS) { moveX -= forwardX; moveZ -= forwardZ; }
        if (keyD) { moveX += strafeX; moveZ += strafeZ; }
        if (keyA) { moveX -= strafeX; moveZ -= strafeZ; }

        double speed = mc.options.keySprint.isDown() ? CAMERA_MOVE_SPEED_FAST : CAMERA_MOVE_SPEED_SLOW;

        double len = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 0.0) {
            moveX = moveX / len * speed;
            moveZ = moveZ / len * speed;
        }

        double moveY = 0.0;
        if (keySpace) moveY += speed;
        if (keyShift) moveY -= speed;

        cameraPos = new Vec3(cameraPos.x + moveX, cameraPos.y + moveY, cameraPos.z + moveZ);

        if (moveY != 0.0D) {
            zoomTargetY += moveY;
        }
        double smoothedY = cameraPos.y + (zoomTargetY - cameraPos.y) * ZOOM_SMOOTHING;
        cameraPos = new Vec3(cameraPos.x, smoothedY, cameraPos.z);

        if (orthoEnabled) {
            updateOrthoHalfWidth();
        }
    }

    public static void onLogout() {
        if (active) exit();
    }
}