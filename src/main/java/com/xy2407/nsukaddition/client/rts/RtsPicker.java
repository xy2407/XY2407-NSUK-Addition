package com.xy2407.nsukaddition.client.rts;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.common.network.rts.RtsAttackTargetPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsSelectionRequestPacket;
import com.xy2407.nsukaddition.common.rts.path.SableStructureReader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** RTS 实体/方块拾取：用相机方向向量+FOV 计算屏幕射线，不依赖投影矩阵。 */
@OnlyIn(Dist.CLIENT)
public final class RtsPicker {

    public static int lastFocusEntityId = -1;

    private static final double PICK_MAX_DISTANCE = 400.0;
    private static final double BOX_SEARCH_RADIUS = 200.0;

    private RtsPicker() {
    }

    public static void clickSelect(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity hit = pickEntityAtScreen(mouseX, mouseY);
        if (hit != null && isSelectable(hit)) {
            if (!RtsModeManager.isSelectionLocked()) {
                sendSelectionRequest(Set.of(hit.getUUID()));
            }
        } else if (hit != null && !RtsModeManager.getSelectedEntities().isEmpty()) {
            if (!canPickAsTarget(hit)) {
                return;
            }
            Set<UUID> selected = new HashSet<>(RtsModeManager.getSelectedEntities());
            RtsModeManager.setAttackTarget(selected, hit.getUUID());
            Map<UUID, Set<UUID>> assign = new HashMap<>();
            for (UUID id : selected) {
                Set<UUID> set = new HashSet<>();
                set.add(hit.getUUID());
                assign.put(id, set);
            }
            PacketDistributor.sendToServer(new RtsAttackTargetPacket(assign));
        } else if (!RtsModeManager.isSelectionLocked()) {
            RtsModeManager.clearSelection();
        }
    }

    public static void boxSelect(double x1, double y1, double x2, double y2) {
        if (RtsModeManager.isSelectionLocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Vec3 forward = getForward(camera);
        Vec3 up = getUp(camera);
        Vec3 right = forward.cross(up).normalize();
        double tanHalfFov = getTanHalfFov(mc);
        double aspect = (double) sw / sh;

        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);

        AABB searchBox = new AABB(
                camPos.x - BOX_SEARCH_RADIUS, camPos.y - BOX_SEARCH_RADIUS, camPos.z - BOX_SEARCH_RADIUS,
                camPos.x + BOX_SEARCH_RADIUS, camPos.y + BOX_SEARCH_RADIUS, camPos.z + BOX_SEARCH_RADIUS);

        Set<UUID> selected = new HashSet<>();
        for (Entity e : mc.level.getEntities(mc.player, searchBox)) {
            if (!isSelectable(e) || !e.isAlive()) continue;
            Vec3 center = e.getBoundingBox().getCenter();
            double[] screen = worldToScreen(center, camPos, forward, right, up, tanHalfFov, aspect, sw, sh);
            if (screen == null) continue;
            if (screen[0] >= minX && screen[0] <= maxX && screen[1] >= minY && screen[1] <= maxY) {
                if (isOccludedByBlocks(mc.level, camPos, e, mc.player)) continue;
                selected.add(e.getUUID());
            }
        }
        sendSelectionRequest(selected);
    }

    private static void sendSelectionRequest(Set<UUID> ids) {
        PacketDistributor.sendToServer(new RtsSelectionRequestPacket(ids == null ? Set.of() : ids));
    }

    private static boolean isSelectable(Entity e) {
        return e instanceof CitizenEntity || e instanceof RtsFakePlayerEntity;
    }

    public static boolean isOwnCityNpc(Entity npc) {
        if (!RtsNpcCache.isReady()) {
            return true;
        }
        return RtsNpcCache.isOwnCityNpc(npc.getUUID());
    }

    public static boolean isRiddenByOwnNpc(Entity mount) {
        if (!(mount instanceof net.minecraft.world.entity.animal.horse.AbstractHorse)) return false;
        net.minecraft.world.entity.Entity rider = mount.getFirstPassenger();
        return rider instanceof CitizenEntity && isOwnCityNpc(rider);
    }

    public static boolean canPickAsTarget(Entity e) {
        return !isRiddenByOwnNpc(e);
    }

    public static void boxSelectAttackTargets(double x1, double y1, double x2, double y2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Vec3 forward = getForward(camera);
        Vec3 up = getUp(camera);
        Vec3 right = forward.cross(up).normalize();
        double tanHalfFov = getTanHalfFov(mc);
        double aspect = (double) sw / sh;

        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);

        AABB searchBox = new AABB(
                camPos.x - BOX_SEARCH_RADIUS, camPos.y - BOX_SEARCH_RADIUS, camPos.z - BOX_SEARCH_RADIUS,
                camPos.x + BOX_SEARCH_RADIUS, camPos.y + BOX_SEARCH_RADIUS, camPos.z + BOX_SEARCH_RADIUS);

        List<Entity> targets = new ArrayList<>();
        for (Entity e : mc.level.getEntities(mc.player, searchBox)) {
            if (!(e instanceof LivingEntity) || !e.isAlive()) continue;
            if (e instanceof CitizenEntity || e instanceof RtsFakePlayerEntity || e instanceof Player) continue;
            if (!canPickAsTarget(e)) continue;
            Vec3 center = e.getBoundingBox().getCenter();
            double[] screen = worldToScreen(center, camPos, forward, right, up, tanHalfFov, aspect, sw, sh);
            if (screen == null) continue;
            if (screen[0] >= minX && screen[0] <= maxX && screen[1] >= minY && screen[1] <= maxY) {
                if (isOccludedByBlocks(mc.level, camPos, e, mc.player)) continue;
                targets.add(e);
            }
        }
        if (targets.isEmpty()) return;

        List<UUID> npcs = new ArrayList<>(RtsModeManager.getSelectedEntities());
        if (npcs.isEmpty()) return;

        Map<UUID, Set<UUID>> assign = new HashMap<>();
        for (UUID npcId : npcs) {
            Set<UUID> set = new HashSet<>();
            for (Entity t : targets) {
                set.add(t.getUUID());
            }
            assign.put(npcId, set);
        }
        if (assign.isEmpty()) return;

        RtsModeManager.setAttackTargets(assign);
        PacketDistributor.sendToServer(new RtsAttackTargetPacket(assign));
    }

    private static Entity findEntity(net.minecraft.client.multiplayer.ClientLevel level, UUID id) {
        if (level == null || id == null) return null;
        for (Entity e : level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) return e;
        }
        return null;
    }

    public static Vec3 pickTargetBlock(double mouseX, double mouseY) {
        BlockHit hit = pickBlockHit(mouseX, mouseY);
        return hit == null ? null : hit.standPos;
    }

    public static final class BlockHit {
        public final BlockPos blockPos;
        public final Direction direction;
        public final Vec3 standPos;
        public final Vec3 location;

        BlockHit(BlockPos pos, Direction dir, Vec3 stand, Vec3 location) {
            this.blockPos = pos;
            this.direction = dir;
            this.standPos = stand;
            this.location = location;
        }
    }

    public static HitResult pickHitResult(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Vec3[] ray = screenToWorldRay(mc, mouseX, mouseY);
        if (ray == null) return null;
        Vec3 origin = ray[0];
        Vec3 end = ray[1];

        AABB box = new AABB(origin, end).inflate(1.0);
        Entity closest = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities((Entity) null, box, Entity::isPickable)) {
            if (e instanceof net.minecraft.world.entity.player.Player) continue;
            AABB bb = e.getBoundingBox();
            if (bb.getSize() < 0.3D) bb = bb.inflate(0.3D);
            Optional<Vec3> clip = bb.clip(origin, end);
            if (clip.isPresent()) {
                double d = clip.get().distanceToSqr(origin);
                if (d < bestSq) {
                    bestSq = d;
                    closest = e;
                }
            }
        }

        ClipContext ctx = new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult blockHit = mc.level.clip(ctx);
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            BlockHitResult any = scanRayAnyBlockHit(mc.level, origin, end);
            if (any != null) {
                blockHit = any;
            }
        }

        if (closest != null) {
            if (blockHit.getType() != HitResult.Type.BLOCK || blockHit.getLocation().distanceToSqr(origin) >= bestSq) {
                return new EntityHitResult(closest);
            }
        }
        return blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    public static BlockHit pickBlockHit(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;

        Vec3[] ray = screenToWorldRay(mc, mouseX, mouseY);
        if (ray == null) return null;
        Vec3 origin = ray[0];
        Vec3 end = ray[1];

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = SableStructureReader.projectOutOfSubLevel(mc.level, blockHit.getLocation());
            Direction face = blockHit.getDirection();
            Vec3 adjusted = loc.subtract(face.getStepX() * 0.001D, face.getStepY() * 0.001D, face.getStepZ() * 0.001D);
            BlockPos hitPos = BlockPos.containing(adjusted);
            Vec3 standPos;
            if (face.getAxis() == Direction.Axis.Y) {
                standPos = new Vec3(hitPos.getX() + 0.5, loc.y, hitPos.getZ() + 0.5);
            } else {
                standPos = resolveStandTarget(mc, hitPos, face);
            }
            return new BlockHit(hitPos, face, standPos, blockHit.getLocation());
        }

        BlockHit fallback = scanRayBlockHit(mc.level, origin, end);
        if (fallback != null) {
            return fallback;
        }

        Vec3 rayDir = end.subtract(origin).normalize();
        if (Math.abs(rayDir.y) < 1.0E-6) {
            return null;
        }
        double t = (mc.player.getY() - origin.y) / rayDir.y;
        if (t <= 0 || t > PICK_MAX_DISTANCE) {
            return null;
        }
        Vec3 proj = origin.add(rayDir.scale(t));
        BlockPos ground = BlockPos.containing(proj);
        return new BlockHit(ground, Direction.UP, new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5),
                new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5));
    }

    private static BlockHit scanRayBlockHit(Level level, Vec3 origin, Vec3 end) {        Vec3 dir = end.subtract(origin).normalize();
        double maxDist = origin.distanceTo(end);
        if (Math.abs(dir.lengthSqr()) < 1.0E-8D) {
            return null;
        }
        final double STEP = 0.2D;
        double t = 0.0D;
        BlockPos prev = null;
        Vec3 last = origin;
        while (t < maxDist) {
            Vec3 p = origin.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if (!pos.equals(prev)) {
                prev = pos;
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                    Direction face = Direction.getNearest(dir.x, dir.y, dir.z).getOpposite();
                    Vec3 stand = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    return new BlockHit(pos, face, stand, last);
                }
                last = p;
            }
            t += STEP;
        }
        return null;
    }

    private static BlockHitResult scanRayAnyBlockHit(Level level, Vec3 origin, Vec3 end) {        Vec3 dir = end.subtract(origin).normalize();
        double maxDist = origin.distanceTo(end);
        if (Math.abs(dir.lengthSqr()) < 1.0E-8D) {
            return null;
        }
        final double STEP = 0.2D;
        double t = 0.0D;
        BlockPos prev = null;
        while (t < maxDist) {
            Vec3 p = origin.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if (!pos.equals(prev)) {
                prev = pos;
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    Direction face = Direction.getNearest(dir.x, dir.y, dir.z).getOpposite();
                    return new BlockHitResult(p, face, pos, false);
                }
            }
            t += STEP;
        }
        return null;
    }

    public static boolean rayHitsAabb(double mouseX, double mouseY, AABB box) {
        return rayHitAabbT(mouseX, mouseY, box) >= 0.0D;
    }

    public static double rayHitAabbT(double mouseX, double mouseY, AABB box) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || box == null) {
            return -1.0D;
        }
        Vec3[] ray = screenToWorldRay(mc, mouseX, mouseY);
        if (ray == null) {
            return -1.0D;
        }
        Vec3 origin = ray[0];
        Vec3 end = ray[1];
        Vec3 dir = end.subtract(origin);
        double tMin = 0.0D;
        double tMax = 1.0D;
        double[] ts = new double[2];
        if (!slabAxis(origin.x, dir.x, box.minX, box.maxX, ts)) return -1.0D;
        tMin = Math.max(tMin, ts[0]); tMax = Math.min(tMax, ts[1]);
        if (tMin > tMax) return -1.0D;
        if (!slabAxis(origin.y, dir.y, box.minY, box.maxY, ts)) return -1.0D;
        tMin = Math.max(tMin, ts[0]); tMax = Math.min(tMax, ts[1]);
        if (tMin > tMax) return -1.0D;
        if (!slabAxis(origin.z, dir.z, box.minZ, box.maxZ, ts)) return -1.0D;
        tMin = Math.max(tMin, ts[0]); tMax = Math.min(tMax, ts[1]);
        if (tMin > tMax) return -1.0D;
        return tMin;
    }

    private static boolean slabAxis(double o, double d, double min, double max, double[] out) {
        if (Math.abs(d) < 1.0E-8D) {
            return o >= min && o <= max;
        }
        double t1 = (min - o) / d;
        double t2 = (max - o) / d;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        out[0] = t1;
        out[1] = t2;
        return true;
    }

    private static Vec3 resolveStandTarget(Minecraft mc, BlockPos hitPos, Direction face) {
        if (face.getAxis().isHorizontal()) {
            BlockPos side = hitPos.relative(face);
            int minY = Math.max(mc.level.getMinBuildHeight() + 1, side.getY() - 16);
            for (int y = side.getY(); y > minY; y--) {
                if (isSolidAt(mc, side.getX(), y, side.getZ())
                        && isAirAt(mc, side.getX(), y + 1, side.getZ())) {
                    return new Vec3(side.getX() + 0.5, y + 1.0, side.getZ() + 0.5);
                }
            }
            return new Vec3(side.getX() + 0.5, side.getY(), side.getZ() + 0.5);
        }
        return new Vec3(hitPos.getX() + 0.5, hitPos.getY() + 1.0, hitPos.getZ() + 0.5);
    }

    private static boolean isSolidAt(Minecraft mc, int x, int y, int z) {
        return mc.level.getBlockState(new BlockPos(x, y, z)).isSolid();
    }

    private static boolean isAirAt(Minecraft mc, int x, int y, int z) {
        return mc.level.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    public static Entity pickEntityAtScreen(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;

        Vec3[] ray = screenToWorldRay(mc, mouseX, mouseY);
        if (ray == null) return null;
        Vec3 origin = ray[0];
        Vec3 end = ray[1];

        AABB searchBox = new AABB(origin, end).inflate(1.0);

        Entity hit = null;
        double minDistSqr = PICK_MAX_DISTANCE * PICK_MAX_DISTANCE;
        for (Entity e : mc.level.getEntities(mc.player, searchBox)) {
            if (!(e instanceof LivingEntity) || !e.isAlive()) continue;
            if (e instanceof net.minecraft.world.entity.player.Player) continue;
            AABB ebox = e.getBoundingBox();
            Optional<Vec3> hitPoint = ebox.clip(origin, end);
            if (hitPoint.isPresent()) {
                double d = origin.distanceToSqr(hitPoint.get());
                if (d < minDistSqr) {
                    minDistSqr = d;
                    hit = e;
                }
            }
        }
        return hit;
    }

    private static Vec3[] screenToWorldRay(Minecraft mc, double mouseX, double mouseY) {
        if (mc.level == null) return null;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Vec3 forward = getForward(camera);
        Vec3 up = getUp(camera);
        Vec3 right = forward.cross(up).normalize();

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        double ndcX = 2.0 * mouseX / sw - 1.0;
        double ndcY = 1.0 - 2.0 * mouseY / sh;

        if (RtsModeManager.isOrthoEnabled()) {
            double halfWidth = RtsModeManager.getOrthoHalfWidth();
            if (halfWidth <= 0) return null;
            double halfHeight = halfWidth * sh / sw;
            Vec3 offset = right.scale(ndcX * halfWidth).add(up.scale(ndcY * halfHeight));
            Vec3 origin = camPos.add(offset);
            Vec3 end = origin.add(forward.scale(PICK_MAX_DISTANCE));
            return new Vec3[]{origin, end};
        }

        double tanHalfFov = getTanHalfFov(mc);
        double aspect = (double) sw / sh;

        Vec3 rayDir = forward
                .add(right.scale(ndcX * tanHalfFov * aspect))
                .add(up.scale(ndcY * tanHalfFov))
                .normalize();

        Vec3 end = camPos.add(rayDir.scale(PICK_MAX_DISTANCE));
        return new Vec3[]{camPos, end};
    }

    private static boolean isOccludedByBlocks(Level level, Vec3 camPos, Entity entity, Entity context) {
        Vec3 from = camPos;
        Vec3 to = entity.getBoundingBox().getCenter();
        double distToEntity = from.distanceTo(to);
        if (distToEntity < 1.0E-4) return false;

        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, context);
        BlockHitResult hit = level.clip(ctx);
        if (hit.getType() == HitResult.Type.BLOCK) {
            double distToHit = hit.getLocation().distanceTo(from);
            return distToHit < distToEntity - 0.05D;
        }
        return false;
    }

    private static double[] worldToScreen(Vec3 worldPos, Vec3 camPos,
                                          Vec3 forward, Vec3 right, Vec3 up,
                                          double tanHalfFov, double aspect,
                                          int sw, int sh) {
        Vec3 toPoint = worldPos.subtract(camPos);

        double forwardDist = toPoint.dot(forward);

        double rightDist = toPoint.dot(right);
        double upDist = toPoint.dot(up);

        if (RtsModeManager.isOrthoEnabled()) {
            double halfWidth = RtsModeManager.getOrthoHalfWidth();
            if (halfWidth <= 0) return null;
            double halfHeight = halfWidth * sh / sw;
            double ndcX = rightDist / halfWidth;
            double ndcY = upDist / halfHeight;
            double screenX = (ndcX + 1.0) * 0.5 * sw;
            double screenY = (1.0 - (ndcY + 1.0) * 0.5) * sh;
            return new double[]{screenX, screenY};
        }

        if (forwardDist <= 0) return null;

        double ndcX = rightDist / (forwardDist * tanHalfFov * aspect);
        double ndcY = upDist / (forwardDist * tanHalfFov);

        double screenX = (ndcX + 1.0) * 0.5 * sw;
        double screenY = (1.0 - (ndcY + 1.0) * 0.5) * sh;
        return new double[]{screenX, screenY};
    }

    private static Vec3 getForward(Camera camera) {
        Quaternionf rot = new Quaternionf(camera.rotation());
        Vector3f v = new Vector3f(0.0F, 0.0F, -1.0F).rotate(rot);
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vec3 getUp(Camera camera) {
        Quaternionf rot = new Quaternionf(camera.rotation());
        Vector3f v = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rot);
        return new Vec3(v.x, v.y, v.z);
    }

    private static double getTanHalfFov(Minecraft mc) {
        int fovDeg = mc.options.fov().get();
        double fovRad = Math.toRadians(fovDeg);
        return Math.tan(fovRad / 2.0);
    }
}