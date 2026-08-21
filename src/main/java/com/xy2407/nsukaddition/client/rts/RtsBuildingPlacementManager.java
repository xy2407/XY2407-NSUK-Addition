package com.xy2407.nsukaddition.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingCacheService;
import client.cn.kafei.simukraft.client.buildbox.PreviewBlockData;
import client.cn.kafei.simukraft.client.buildbox.PreviewMesh;
import client.cn.kafei.simukraft.client.buildbox.PreviewMeshBuilder;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import com.xy2407.nsukaddition.common.network.rts.RtsBuildingMovePacket;
import com.xy2407.nsukaddition.common.network.rts.RtsPlacedBuildingSyncPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsStartBuildingPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsStartBuildingResultPacket;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingTerritoryValidator;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 建筑放置/迁移状态机：
 * - 放置：选建筑 → 投影跟随 → 右键固化到待注入列表(不直接建任务) → Enter 按顺序注入 → C 按顺序撤回；
 * - 迁移：选中建筑师/规划师时左键点击已放置建筑界限 → 投影跟随 → 右键迁移(服务端清除旧数据并重建)。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsBuildingPlacementManager {

    private static boolean active;
    private static UUID builderId;
    private static BuildingCacheService.BuildingMeta meta;
    private static BuildingStructure structure;
    private static List<BuildingBlockData> localBlocks = List.of();
    private static PreviewMesh mesh;
    private static int rotation;
    private static int heightOffset;
    private static double baseY;
    private static Vec3 origin;
    private static Vec3 mouseGround;
    private static boolean valid;
    private static BlockPos lastCheckedOrigin;

    public static final class PendingPlacement {
        private final UUID builderId;
        private final BuildingCacheService.BuildingMeta meta;
        private final BuildingStructure structure;
        private List<BuildingBlockData> localBlocks;
        private PreviewMesh mesh;
        private volatile BlockPos origin;
        private int rotation;
        private final int heightOffset;

        PendingPlacement(UUID builderId, BuildingCacheService.BuildingMeta meta,
                         BuildingStructure structure, List<BuildingBlockData> localBlocks,
                         PreviewMesh mesh, BlockPos origin, int rotation, int heightOffset) {
            this.builderId = builderId;
            this.meta = meta;
            this.structure = structure;
            this.localBlocks = localBlocks;
            this.mesh = mesh;
            this.origin = origin;
            this.rotation = rotation;
            this.heightOffset = heightOffset;
        }

        void applyRotation(int newRotation, List<BuildingBlockData> newBlocks, PreviewMesh newMesh) {
            this.rotation = Math.floorMod(newRotation, 360);
            this.localBlocks = newBlocks;
            if (this.mesh != null) {
                this.mesh.close();
            }
            this.mesh = newMesh;
        }

        UUID builderId() { return builderId; }
        BuildingCacheService.BuildingMeta meta() { return meta; }
        BuildingStructure structure() { return structure; }
        List<BuildingBlockData> localBlocks() { return localBlocks; }
        PreviewMesh mesh() { return mesh; }
        BlockPos origin() { return origin; }
        int rotation() { return rotation; }
        int heightOffset() { return heightOffset; }
        void closeMesh() { mesh.close(); }
    }

    private static final List<PendingPlacement> pendingPlacements = new ArrayList<>();

    private static boolean moveActive;
    private static RtsPlacedBuildingSyncPacket.Entry moveEntry;
    private static BuildingStructure moveStructure;
    private static List<BuildingBlockData> moveLocalBlocks = List.of();
    private static PreviewMesh moveMesh;
    private static Vec3 moveOrigin;
    private static int moveRotation;
    private static double moveBaseY;
    private static boolean moveValid;
    private static BlockPos lastMoveCheckedOrigin;
    private static boolean moveDragging;
    private static Vec3 dragGroundStart;
    private static Vec3 dragOriginStart;

    private RtsBuildingPlacementManager() {
    }

    public static boolean isActive() {
        return active;
    }

    public static UUID getBuilderId() {
        return builderId;
    }

    public static BuildingCacheService.BuildingMeta getMeta() {
        return meta;
    }

    public static Vec3 getOrigin() {
        return origin;
    }

    public static int getRotation() {
        return rotation;
    }

    public static int getHeightOffset() {
        return heightOffset;
    }

    public static boolean isValid() {
        return valid;
    }

    public static PreviewMesh getMesh() {
        return mesh;
    }

    public static BlockPos getBuildingSize() {
        return structure != null ? structure.size() : null;
    }

    public static List<PendingPlacement> getPendingPlacements() {
        return List.copyOf(pendingPlacements);
    }

    public static boolean hasPending() {
        return !pendingPlacements.isEmpty();
    }

    public static boolean isMoveActive() {
        return moveActive;
    }

    public static Vec3 getMoveOrigin() {
        return moveOrigin;
    }

    public static int getMoveRotation() {
        return moveRotation;
    }

    public static boolean isMoveValid() {
        return moveValid;
    }

    public static PreviewMesh getMoveMesh() {
        return moveMesh;
    }

    public static RtsPlacedBuildingSyncPacket.Entry getMoveEntry() {
        return moveEntry;
    }

    public static BlockPos getMoveBuildingSize() {
        return moveStructure != null ? moveStructure.size() : null;
    }

    public static AABB getMoveAabb() {
        if (moveStructure == null || moveOrigin == null) {
            return null;
        }
        return moveAabbAt(BlockPos.containing(moveOrigin));
    }

    public static AABB getPlacementAabb() {
        return placementAabb();
    }

    public static void startPlacement(UUID builderId, BuildingCacheService.BuildingMeta meta, Vec3 npcPos) {
        deactivate();
        if (builderId == null || meta == null || npcPos == null) {
            return;
        }
        BuildingStructure loaded = BuildingStructureService.loadStructure(meta.category(), meta.metaFileName()).orElse(null);
        if (loaded == null || loaded.blocks().isEmpty()) {
            return;
        }
        RtsBuildingPlacementManager.builderId = builderId;
        RtsBuildingPlacementManager.meta = meta;
        RtsBuildingPlacementManager.structure = loaded;
        rotation = 0;
        heightOffset = 0;
        baseY = Math.floor(npcPos.y);
        origin = new Vec3(Math.floor(npcPos.x), baseY, Math.floor(npcPos.z));
        mouseGround = origin;
        valid = false;
        lastCheckedOrigin = null;
        rebuildLocalBlocks();
        refreshValidity();
        active = true;
    }

    public static void tick() {
        if (!active) {
            return;
        }
        Set<UUID> selected = RtsModeManager.getSelectedEntities();
        if (builderId != null && (selected == null || !selected.contains(builderId))) {
            deactivate();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        double[] pos = RtsModeManager.getGuiScaledMouse();
        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(pos[0], pos[1]);
        if (hit != null) {
            mouseGround = hit.standPos;
        }
        Vec3 candidate = new Vec3(mouseGround.x, baseY + heightOffset, mouseGround.z);
        BlockPos candidateBlock = BlockPos.containing(candidate);
        if (lastCheckedOrigin == null || !lastCheckedOrigin.equals(candidateBlock)) {
            valid = isInCity(candidateBlock);
            lastCheckedOrigin = candidateBlock;
            if (valid) {
                origin = new Vec3(candidateBlock.getX(), candidateBlock.getY(), candidateBlock.getZ());
            }
        }
    }

    public static void rotate() {
        if (!active) {
            return;
        }
        rotation = (rotation + 90) % 360;
        rebuildLocalBlocks();
        refreshValidity();
    }

    public static void adjustHeight(int delta) {
        if (!active || delta == 0) {
            return;
        }
        heightOffset += delta;
        Vec3 candidate = new Vec3(mouseGround.x, baseY + heightOffset, mouseGround.z);
        BlockPos candidateBlock = BlockPos.containing(candidate);
        valid = isInCity(candidateBlock);
        lastCheckedOrigin = candidateBlock;
        if (valid) {
            origin = new Vec3(candidateBlock.getX(), candidateBlock.getY(), candidateBlock.getZ());
        }
    }

    public static void confirmPlace() {
        if (!active || !valid || meta == null) {
            return;
        }
        List<BuildingBlockData> snapshot = new ArrayList<>(localBlocks);
        PreviewMesh pendingMesh = buildMesh(snapshot);
        pendingPlacements.add(new PendingPlacement(builderId, meta, structure, snapshot, pendingMesh,
                BlockPos.containing(origin), rotation, heightOffset));
        deactivate();
    }

    public static void undoLastPlacement() {
        if (pendingPlacements.isEmpty()) {
            return;
        }
        PendingPlacement last = pendingPlacements.remove(pendingPlacements.size() - 1);
        last.closeMesh();
    }

    public static void clearPending() {
        for (PendingPlacement p : pendingPlacements) {
            p.closeMesh();
        }
        pendingPlacements.clear();
        endPendingDrag();
    }

    public static boolean hasPendingPlacements() {
        return !pendingPlacements.isEmpty();
    }

    public static boolean adjustPendingHeight(int delta) {
        if (pendingPlacements.isEmpty() || delta == 0) {
            return false;
        }
        PendingPlacement p = pendingDragIndex >= 0
                ? pendingPlacements.get(pendingDragIndex)
                : pendingPlacements.get(pendingPlacements.size() - 1);
        BlockPos o = p.origin();
        p.origin = new BlockPos(o.getX(), o.getY() + delta, o.getZ());
        return true;
    }

    public static boolean rotatePending() {
        if (pendingPlacements.isEmpty()) {
            return false;
        }
        PendingPlacement p = pendingDragIndex >= 0
                ? pendingPlacements.get(pendingDragIndex)
                : pendingPlacements.get(pendingPlacements.size() - 1);
        int newRotation = Math.floorMod(p.rotation() + 90, 360);
        List<BuildingBlockData> newBlocks = BuildingStructureService.resolvePlacedBlocks(p.structure(), BlockPos.ZERO, newRotation);
        p.applyRotation(newRotation, newBlocks, buildMesh(newBlocks));
        return true;
    }

    private static int pendingDragIndex = -1;
    private static Vec3 pendingDragGroundStart;
    private static BlockPos pendingDragOriginStart;

    public static boolean isPendingDragging() {
        return pendingDragIndex >= 0;
    }

    public static boolean beginPendingDrag(double screenX, double screenY) {
        if (active || moveActive || pendingDragIndex >= 0) {
            return false;
        }
        int idx = hitPendingIndex(screenX, screenY);
        if (idx < 0) {
            return false;
        }
        pendingDragIndex = idx;
        double[] pos = RtsModeManager.getGuiScaledMouse();
        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(pos[0], pos[1]);
        PendingPlacement p = pendingPlacements.get(idx);
        pendingDragGroundStart = hit != null ? hit.standPos : Vec3.atBottomCenterOf(p.origin());
        pendingDragOriginStart = p.origin();
        return true;
    }

    private static int hitPendingIndex(double screenX, double screenY) {
        int best = -1;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < pendingPlacements.size(); i++) {
            PendingPlacement p = pendingPlacements.get(i);
            AABB box = aabbOfResolved(p.structure(), p.origin(), p.rotation());
            if (box == null) {
                continue;
            }
            double t = RtsPicker.rayHitAabbT(screenX, screenY, box);
            if (t >= 0.0D && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    public static void tickPendingDrag() {
        if (pendingDragIndex < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            endPendingDrag();
            return;
        }
        if (pendingDragGroundStart == null || pendingDragOriginStart == null) {
            return;
        }
        double[] pos = RtsModeManager.getGuiScaledMouse();
        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(pos[0], pos[1]);
        if (hit == null) {
            return;
        }
        int dx = (int) Math.floor(hit.standPos.x - pendingDragGroundStart.x);
        int dz = (int) Math.floor(hit.standPos.z - pendingDragGroundStart.z);
        PendingPlacement p = pendingPlacements.get(pendingDragIndex);
        p.origin = new BlockPos(
                pendingDragOriginStart.getX() + dx,
                p.origin().getY(),
                pendingDragOriginStart.getZ() + dz);
    }

    public static void endPendingDrag() {
        pendingDragIndex = -1;
        pendingDragGroundStart = null;
        pendingDragOriginStart = null;
    }

    public static void confirmAll() {
        if (pendingPlacements.isEmpty()) {
            return;
        }
        for (PendingPlacement p : pendingPlacements) {
            PacketDistributor.sendToServer(new RtsStartBuildingPacket(
                    p.builderId(), p.meta().category(), p.meta().metaFileName(), p.origin(), p.rotation()));
        }
    }

    public static void onResult(RtsStartBuildingResultPacket result) {
        boolean removed = false;
        for (int i = pendingPlacements.size() - 1; i >= 0; i--) {
            PendingPlacement p = pendingPlacements.get(i);
            if (p.origin().equals(result.origin()) && p.rotation() == result.rotation()
                    && p.meta().category().equals(result.category())) {
                if (result.success()) {
                    p.closeMesh();
                    pendingPlacements.remove(i);
                    removed = true;
                }
                break;
            }
        }
        if (!removed && !result.success()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String msg = "§c建造注入失败: " + reasonText(result.reason()) + " §7(按 C 撤回该投影)";
                mc.gui.setOverlayMessage(Component.literal(msg), false);
            }
        }
    }

    private static String reasonText(String reason) {
        if (reason == null) {
            return "未知原因";
        }
        return switch (reason) {
            case "not_enough_funds" -> "资金不足";
            case "outside_city" -> "超出城市领地";
            case "no_permission" -> "无权限";
            case "build_box_not_found" -> "建筑师无建筑盒";
            case "structure_not_found" -> "建筑结构缺失";
            case "task_limit_reached" -> "该建筑师任务已达上限(256)";
            default -> "失败";
        };
    }

    public static void deactivate() {
        active = false;
        builderId = null;
        meta = null;
        structure = null;
        localBlocks = List.of();
        rotation = 0;
        heightOffset = 0;
        origin = null;
        mouseGround = null;
        valid = false;
        lastCheckedOrigin = null;
        releaseMesh();
    }

    private static void rebuildLocalBlocks() {
        if (structure == null) {
            return;
        }
        localBlocks = BuildingStructureService.resolvePlacedBlocks(structure, BlockPos.ZERO, rotation);
        releaseMesh();
        mesh = buildMesh(localBlocks);
    }

    private static PreviewMesh buildMesh(List<BuildingBlockData> blocks) {
        List<PreviewBlockData> previewBlocks = new ArrayList<>(blocks.size());
        for (BuildingBlockData block : blocks) {
            previewBlocks.add(new PreviewBlockData(block.relativePos(), block.state(), LightTexture.FULL_BLOCK,
                    block.copyBlockEntityData()));
        }
        return PreviewMeshBuilder.build(previewBlocks);
    }

    private static boolean isInCity(BlockPos originBlock) {
        if (structure == null) {
            return false;
        }
        List<BlockPos> worldPoses = BuildingStructureService.resolvePlacedBlocks(structure, originBlock, rotation)
                .stream().map(BuildingBlockData::relativePos).toList();
        return BuildingTerritoryValidator.positionBoundsInChunks(worldPoses, ClientCityChunkCache.getInstance().getCurrentCityChunks());
    }

    private static void refreshValidity() {
        if (origin == null) {
            valid = false;
            return;
        }
        BlockPos originBlock = BlockPos.containing(origin);
        valid = isInCity(originBlock) && !intersectsPlacedBuilding(placementAabb(), null);
        lastCheckedOrigin = originBlock;
    }

    private static void releaseMesh() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
    }

    public static boolean tryStartMoveFromClick(double screenX, double screenY) {
        if (moveActive || active) {
            return false;
        }
        if (!RtsPlacedBuildingCache.hasBuilderOrPlanner(RtsModeManager.getSelectedEntities())) {
            return false;
        }
        if (RtsPicker.pickEntityAtScreen(screenX, screenY) != null) {
            return false;
        }
        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(screenX, screenY);
        if (hit == null) {
            return false;
        }
        RtsPlacedBuildingSyncPacket.Entry entry = RtsPlacedBuildingCache.pickBuildingAt(hit.standPos);
        if (entry == null) {
            return false;
        }
        startMove(entry, hit.standPos);
        return true;
    }

    private static void startMove(RtsPlacedBuildingSyncPacket.Entry entry, Vec3 ground) {
        BuildingStructure loaded = BuildingStructureService.loadStructure(entry.category(), entry.buildingFileName()).orElse(null);
        if (loaded == null || loaded.blocks().isEmpty()) {
            return;
        }
        moveEntry = entry;
        moveStructure = loaded;
        moveRotation = entry.rotation();
        BlockPos entryOrigin = entry.origin() != null ? entry.origin() : BlockPos.containing(ground);
        moveBaseY = entryOrigin.getY();
        moveOrigin = new Vec3(entryOrigin.getX(), moveBaseY, entryOrigin.getZ());
        moveDragging = false;
        dragGroundStart = null;
        dragOriginStart = null;
        moveValid = false;
        lastMoveCheckedOrigin = null;
        rebuildMoveMesh();
        refreshMoveValidity();
        moveActive = true;
    }

    public static void tickMove() {
        if (!moveActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        if (moveDragging) {
            updateMoveDrag();
        }
    }

    public static void beginMoveDrag() {
        if (!moveActive || moveDragging) {
            return;
        }
        double[] pos = RtsModeManager.getGuiScaledMouse();
        AABB proj = moveAabbAt(BlockPos.containing(moveOrigin));
        if (proj == null || !RtsPicker.rayHitsAabb(pos[0], pos[1], proj)) {
            return;
        }
        moveDragging = true;
        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(pos[0], pos[1]);
        dragGroundStart = hit != null ? hit.standPos : moveOrigin;
        dragOriginStart = moveOrigin;
    }

    public static void updateMoveDrag() {
        if (!moveActive || !moveDragging || dragGroundStart == null || dragOriginStart == null) {
            return;
        }
        double[] pos = RtsModeManager.getGuiScaledMouse();
        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(pos[0], pos[1]);
        if (hit == null) {
            return;
        }
        Vec3 ground = hit.standPos;
        int dx = (int) Math.floor(ground.x - dragGroundStart.x);
        int dz = (int) Math.floor(ground.z - dragGroundStart.z);
        applyMoveOrigin(dragOriginStart.x + dx, dragOriginStart.z + dz);
    }

    public static void endMoveDrag() {
        moveDragging = false;
        dragGroundStart = null;
        dragOriginStart = null;
    }

    public static void rotateMove() {
        if (!moveActive) {
            return;
        }
        moveRotation = (moveRotation + 90) % 360;
        rebuildMoveMesh();
        refreshMoveValidity();
    }

    public static void adjustMoveHeight(int delta) {
        if (!moveActive || delta == 0) {
            return;
        }
        moveBaseY += delta;
        applyMoveOrigin(moveOrigin.x, moveOrigin.z);
    }

    private static void applyMoveOrigin(double x, double z) {
        BlockPos candidate = new BlockPos((int) Math.floor(x), (int) Math.floor(moveBaseY), (int) Math.floor(z));
        if (lastMoveCheckedOrigin != null && lastMoveCheckedOrigin.equals(candidate)) {
            return;
        }
        moveValid = isMoveInCity(candidate);
        lastMoveCheckedOrigin = candidate;
        if (moveValid) {
            moveOrigin = new Vec3(candidate.getX(), candidate.getY(), candidate.getZ());
        }
    }

    public static void confirmMove() {
        if (!moveActive || !moveValid || moveEntry == null) {
            return;
        }
        PacketDistributor.sendToServer(new RtsBuildingMovePacket(
                moveEntry.buildingId(), BlockPos.containing(moveOrigin), moveRotation));
        endMove();
    }

    public static void endMove() {
        moveActive = false;
        moveEntry = null;
        moveStructure = null;
        moveLocalBlocks = List.of();
        moveRotation = 0;
        moveOrigin = null;
        moveDragging = false;
        dragGroundStart = null;
        dragOriginStart = null;
        moveValid = false;
        lastMoveCheckedOrigin = null;
        if (moveMesh != null) {
            moveMesh.close();
            moveMesh = null;
        }
    }

    private static void rebuildMoveMesh() {
        if (moveStructure == null) {
            return;
        }
        moveLocalBlocks = BuildingStructureService.resolvePlacedBlocks(moveStructure, BlockPos.ZERO, moveRotation);
        if (moveMesh != null) {
            moveMesh.close();
            moveMesh = null;
        }
        moveMesh = buildMesh(moveLocalBlocks);
    }

    private static boolean isMoveInCity(BlockPos originBlock) {
        if (moveStructure == null) {
            return false;
        }
        List<BlockPos> worldPoses = BuildingStructureService.resolvePlacedBlocks(moveStructure, originBlock, moveRotation)
                .stream().map(BuildingBlockData::relativePos).toList();
        return BuildingTerritoryValidator.positionBoundsInChunks(worldPoses, ClientCityChunkCache.getInstance().getCurrentCityChunks());
    }

    private static void refreshMoveValidity() {
        if (moveOrigin == null) {
            moveValid = false;
            return;
        }
        BlockPos originBlock = BlockPos.containing(moveOrigin);
        moveValid = isMoveInCity(originBlock);
        lastMoveCheckedOrigin = originBlock;
    }

    private static AABB placementAabb() {
        if (structure == null || origin == null) {
            return null;
        }
        return aabbOfResolved(structure, BlockPos.containing(origin), rotation);
    }

    private static AABB moveAabbAt(BlockPos originBlock) {
        if (moveStructure == null || originBlock == null) {
            return null;
        }
        return aabbOfResolved(moveStructure, originBlock, moveRotation);
    }

    private static AABB aabbOfResolved(BuildingStructure structure, BlockPos originBlock, int rotation) {
        List<BlockPos> poses = BuildingStructureService.resolvePlacedBlocks(structure, originBlock, rotation)
                .stream().map(BuildingBlockData::relativePos).toList();
        if (poses.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : poses) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    private static boolean intersectsPlacedBuilding(AABB preview, UUID excludeBuildingId) {
        if (preview == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel cl) || mc.getSingleplayerServer() == null) {
            return false;
        }
        ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(cl.dimension());
        if (serverLevel == null) {
            return false;
        }
        for (PlacedBuildingRecord b : PlacedBuildingService.getBuildings(serverLevel)) {
            if (excludeBuildingId != null && excludeBuildingId.equals(b.buildingId())) {
                continue;
            }
            if (b.minPos() == null || b.maxPos() == null) {
                continue;
            }
            AABB bb = new AABB(b.minPos().getX(), b.minPos().getY(), b.minPos().getZ(),
                    b.maxPos().getX() + 1.0, b.maxPos().getY() + 1.0, b.maxPos().getZ() + 1.0);
            if (preview.inflate(0.001D).intersects(bb)) {
                return true;
            }
        }
        return false;
    }
}