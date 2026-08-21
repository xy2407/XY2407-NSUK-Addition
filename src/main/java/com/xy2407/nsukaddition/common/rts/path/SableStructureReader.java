package com.xy2407.nsukaddition.common.rts.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Sable/航空学物理结构读取桥（反射 + 一次性缓存）。
 *
 * 全部签名已对照 Sable 新版源码验证，反射面只取各版本稳定的基础公共 API：
 * - Sable.HELPER（Sable.java:35，public static final ActiveSableCompanion）
 * - ActiveSableCompanion.getAllIntersecting(Level, BoundingBox3dc)（ActiveSableCompanion.java:50）
 * - ActiveSableCompanion.getContaining(Level, Vec3i)（ActiveSableCompanion.java:92）
 * - SubLevel.logicalPose() / getLevel()（SubLevel.java:151/143）
 * - Pose3dc.transformPosition(Vec3) / transformPositionInverse(Vec3)
 * - BoundingBox3d(Vec3, Vec3)
 * - SubLevelContainer.getContainer(Level) + getLoadedCount()（用于廉价的结构存在性探测）
 *
 * 快照合并原理：结构方块存于 plot 真实坐标（远端），服务端 capture 扫描主世界坐标时，
 * 用 getAllIntersecting 命中 sublevel，再 transformPositionInverse 把世界坐标转结构局部坐标，
 * 在父 Level 读取结构方块喂进快照。未安装 Sable 或 API 探测失败时全部安全降级。
 * 注意：本类不做 chunk 级缓存，getBlockStateAt 每次做单点精确查询，capture 低频可接受。
 */
public final class SableStructureReader {

    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";
    private static final String HELPER_FIELD = "HELPER";
    private static final String GET_CONTAINING = "getContaining";
    private static final String LOGICAL_POSE = "logicalPose";
    private static final String GET_LEVEL = "getLevel";
    private static final String GET_ALL_INTERSECTING = "getAllIntersecting";
    private static final String TRANSFORM_POSITION = "transformPosition";
    private static final String TRANSFORM_POSITION_INVERSE = "transformPositionInverse";
    private static final String POSE3DC_CLASS = "dev.ryanhcode.sable.companion.math.Pose3dc";
    private static final String BB3D_CLASS = "dev.ryanhcode.sable.companion.math.BoundingBox3d";
    private static final String BB3DC_CLASS = "dev.ryanhcode.sable.companion.math.BoundingBox3dc";
    private static final String SUB_LEVEL_CONTAINER_CLASS = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";

    private static volatile Boolean available = null;
    private static Object helper = null;
    private static Method getContainingMethod = null;
    private static Method logicalPoseMethod = null;
    private static Method getLevelMethod = null;
    private static Method transformPositionInverseMethod = null;
    private static Method transformPositionMethod = null;
    private static Method getAllIntersectingMethod = null;
    private static Constructor<?> boundingBoxCtor = null;
    private static Method getContainerMethod = null;
    private static Method getLoadedCountMethod = null;

    private static volatile boolean anyStructure = false;
    private static volatile long lastAnyQueryTick = Long.MIN_VALUE;
    private static final long ANY_QUERY_INTERVAL_TICKS = 20L;

    private SableStructureReader() {
    }

    private static void probe() {
        if (available != null) return;
        synchronized (SableStructureReader.class) {
            if (available != null) return;
            try {
                Class<?> sableClass = Class.forName(SABLE_CLASS);
                Field helperField = sableClass.getField(HELPER_FIELD);
                helper = helperField.get(null);
                Class<?> companionClass = helper.getClass();
                getContainingMethod = companionClass.getMethod(GET_CONTAINING, Level.class, net.minecraft.core.Vec3i.class);
                getLevelMethod = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel").getMethod(GET_LEVEL);
                logicalPoseMethod = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel").getMethod(LOGICAL_POSE);
                transformPositionInverseMethod = Class.forName(POSE3DC_CLASS).getMethod(TRANSFORM_POSITION_INVERSE, Vec3.class);
                transformPositionMethod = Class.forName(POSE3DC_CLASS).getMethod(TRANSFORM_POSITION, Vec3.class);
                getAllIntersectingMethod = companionClass.getMethod(GET_ALL_INTERSECTING, Level.class, Class.forName(BB3DC_CLASS));
                boundingBoxCtor = Class.forName(BB3D_CLASS).getConstructor(Vec3.class, Vec3.class);
                Class<?> containerClass = Class.forName(SUB_LEVEL_CONTAINER_CLASS);
                getContainerMethod = containerClass.getMethod("getContainer", Level.class);
                getLoadedCountMethod = containerClass.getMethod("getLoadedCount");
                available = Boolean.TRUE;
            } catch (Throwable t) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SableStructureReader.class);
                logger.warn("[nsuk] Sable API 探测失败，物理结构兼容已降级（结构障碍将不可见）: {}", String.valueOf(t));
                available = Boolean.FALSE;
                clearCachedApis();
            }
        }
    }

    private static void clearCachedApis() {
        helper = null;
        getContainingMethod = null;
        logicalPoseMethod = null;
        getLevelMethod = null;
        transformPositionInverseMethod = null;
        transformPositionMethod = null;
        getAllIntersectingMethod = null;
        boundingBoxCtor = null;
        getContainerMethod = null;
        getLoadedCountMethod = null;
    }

    public static boolean isAvailable() {
        probe();
        return available == Boolean.TRUE;
    }

    public static Vec3 projectOutOfSubLevel(Level level, Vec3 pos) {
        if (!isAvailable() || level == null || pos == null) return pos;
        try {
            Object sub = getContainingMethod.invoke(helper, level, BlockPos.containing(pos));
            if (sub == null) return pos;
            Object worldObj = transformPositionMethod.invoke(logicalPoseMethod.invoke(sub), pos);
            return worldObj instanceof Vec3 world ? world : pos;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return pos;
        }
    }

    public record SubLevelBlock(Level ownerLevel, BlockPos plotPos, BlockState state) {
    }

    public static BlockState getBlockStateAt(Level level, BlockPos pos) {
        SubLevelBlock sub = getSubLevelBlockAt(level, pos);
        return sub == null ? null : sub.state();
    }

    public static SubLevelBlock getSubLevelBlockAt(Level level, BlockPos pos) {
        if (!isAvailable() || level == null || pos == null) return null;
        try {
            Vec3 world = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double e = 0.1;
            Object bounds = boundingBoxCtor.newInstance(world.add(-e, -e, -e), world.add(e, e, e));
            Object iterable = getAllIntersectingMethod.invoke(helper, level, bounds);
            java.util.List<Object> hits = new java.util.ArrayList<>();
            for (Object obj : (Iterable<?>) iterable) {
                hits.add(obj);
            }
            SubLevelBlock read = null;
            for (Object sub : hits) {
                Object pose = logicalPoseMethod.invoke(sub);
                Object localObj = transformPositionInverseMethod.invoke(pose, world);
                if (!(localObj instanceof Vec3 local)) continue;
                Object parentLevel = getLevelMethod.invoke(sub);
                if (parentLevel instanceof Level lv) {
                    BlockState state = lv.getBlockState(BlockPos.containing(local));
                    if (state != null && !state.isAir()) {
                        read = new SubLevelBlock(lv, BlockPos.containing(local), state);
                        break;
                    }
                }
            }
            return read;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public record StructureAnchor(Level ownerLevel, Object sublevel, Vec3 plotLocalTarget) {
    }

    public static StructureAnchor resolveAnchor(Level level, Vec3 worldPos) {
        if (!isAvailable() || level == null || worldPos == null) return null;
        try {
            double e = 0.1;
            Object bounds = boundingBoxCtor.newInstance(worldPos.add(-e, -e, -e), worldPos.add(e, e, e));
            Object iterable = getAllIntersectingMethod.invoke(helper, level, bounds);
            for (Object sub : (Iterable<?>) iterable) {
                Object pose = logicalPoseMethod.invoke(sub);
                Object localObj = transformPositionInverseMethod.invoke(pose, worldPos);
                if (!(localObj instanceof Vec3 local)) continue;
                Object owner = getLevelMethod.invoke(sub);
                if (owner instanceof Level lv) {
                    return new StructureAnchor(lv, sub, local);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    public static Vec3 anchorToWorld(StructureAnchor anchor) {
        if (anchor == null || anchor.sublevel() == null || anchor.plotLocalTarget() == null) return null;
        if (!isAvailable()) return null;
        try {
            Object sub = getContainingMethod.invoke(helper, anchor.ownerLevel(), BlockPos.containing(anchor.plotLocalTarget()));
            if (sub != anchor.sublevel()) return null;
            Object worldObj = transformPositionMethod.invoke(logicalPoseMethod.invoke(anchor.sublevel()), anchor.plotLocalTarget());
            return worldObj instanceof Vec3 w ? w : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public static boolean mayContainStructure(Level level) {
        if (!isAvailable() || level == null) {
            return false;
        }
        if (anyStructure) {
            return true;
        }
        long now = level.getGameTime();
        if (now - lastAnyQueryTick >= ANY_QUERY_INTERVAL_TICKS) {
            refreshAnyStructure(level);
            lastAnyQueryTick = now;
        }
        return anyStructure;
    }

    private static void refreshAnyStructure(Level level) {
        try {
            Object container = getContainerMethod.invoke(null, level);
            if (container != null) {
                Object count = getLoadedCountMethod.invoke(container);
                anyStructure = count instanceof Integer c && c > 0;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            anyStructure = true;
        }
    }
}