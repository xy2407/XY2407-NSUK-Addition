package com.xy2407.nsukaddition.common.rts.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
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
    private static final String GET_PLOT = "getPlot";
    private static final String TO_LOCAL = "toLocal";
    private static final String PLOT_GET_CHUNK_HOLDER = "getChunkHolder";
    private static final String PLOT_GET_CHUNK = "getChunk";
    private static final String LEVEL_PLOT_CLASS = "dev.ryanhcode.sable.sublevel.plot.LevelPlot";
    private static final String PLOT_CHUNK_HOLDER_CLASS = "dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder";

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
    private static Method getPlotMethod = null;
    private static Method plotToLocalMethod = null;
    private static Method plotGetChunkHolderMethod = null;
    private static Method plotGetChunkMethod = null;

    private static boolean anyStructure = false;
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
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                getPlotMethod = subLevelClass.getMethod(GET_PLOT);
                Class<?> levelPlotClass = Class.forName(LEVEL_PLOT_CLASS);
                plotToLocalMethod = levelPlotClass.getMethod(TO_LOCAL, ChunkPos.class);
                plotGetChunkHolderMethod = levelPlotClass.getMethod(PLOT_GET_CHUNK_HOLDER, ChunkPos.class);
                plotGetChunkMethod = Class.forName(PLOT_CHUNK_HOLDER_CLASS).getMethod(PLOT_GET_CHUNK);
                available = Boolean.TRUE;
            } catch (Throwable t) {
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
        getPlotMethod = null;
        plotToLocalMethod = null;
        plotGetChunkHolderMethod = null;
        plotGetChunkMethod = null;
    }

    public static boolean isAvailable() {
        probe();
        return available == Boolean.TRUE;
    }

    /**
     * 把 Sable 结构命中点投影回父世界坐标。物理化结构内容存放于 plot 远端坐标，clip/选中点
     * 可能落在这些远端坐标上，必须用当前 pose transformPosition 映射到父世界才能作为寻路目标；
     * 不在结构内则原样返回。不可到达的远端坐标场景（修正旋转目标时必须保留此投影）。
     */
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

    /**
     * 任意角旋转结构的半格膨胀读取：先按中心点读结构状态；自身为空气时，若命中某个
     * 非轴对齐水平旋转结构的邻接格子，则返回一个占位实心方块，让 A* 在这条倾斜边外围
     * 留出约半格到一格的安全边距，避免 NPC 贴着格子路径撞到实际倾斜/错位碰撞面的边角。
     * 轴对齐结构不被膨胀，完全保留 SimuKraft 原先的贴墙精度。
     */
    public static BlockState getInflatedBlockStateAt(Level level, BlockPos pos) {
        SubLevelBlock sub = getSubLevelBlockAt(level, pos);
        if (sub != null) {
            return sub.state();
        }
        if (!anyStructure) {
            return null;
        }
        BlockPos.MutableBlockPos nb = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            nb.set(pos).move(d);
            SubLevelBlock neighbor = getSubLevelBlockAt(level, nb);
            if (neighbor == null) continue;
            if (isRotatedPoseAt(level, nb)) {
                return Blocks.STONE.defaultBlockState();
            }
        }
        return null;
    }

    /** 判断世界格子处是否命中一个"任意角"水平旋转结构（非 90° 整数倍）。 */
    private static boolean isRotatedPoseAt(Level level, BlockPos pos) {
        if (!isAvailable()) return false;
        try {
            Vec3 world = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double e = 0.1;
            Object bounds = boundingBoxCtor.newInstance(world.add(-e, -e, -e), world.add(e, e, e));
            Object iterable = getAllIntersectingMethod.invoke(helper, level, bounds);
            for (Object obj : (Iterable<?>) iterable) {
                Object pose = logicalPoseMethod.invoke(obj);
                if (isArbitraryYaw(pose, world)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }

    /** 用世界 X 方向向量的局部像判断水平偏航是否为任意角。 */
    private static boolean isArbitraryYaw(Object pose, Vec3 world) throws ReflectiveOperationException {
        Vec3 l1 = (Vec3) transformPositionInverseMethod.invoke(pose, world);
        Vec3 l2 = (Vec3) transformPositionInverseMethod.invoke(pose, world.add(1.0, 0.0, 0.0));
        double dx = l2.x - l1.x;
        double dz = l2.z - l1.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1.0E-6D) {
            return false;
        }
        double cosX = Math.abs(dx / h);
        return cosX > 0.05D && cosX < 0.995D;
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
                BlockPos absPos = BlockPos.containing(local);
                if (parentLevel instanceof Level lv) {
                    BlockState state = lv.getBlockState(absPos);
                    if (state != null && !state.isAir()) {
                        read = new SubLevelBlock(lv, absPos, state);
                        break;
                    }
                }
                BlockState plotState = readFromPlot(sub, absPos);
                if (plotState != null && !plotState.isAir()) {
                    read = new SubLevelBlock(parentLevel instanceof Level lv ? lv : level, absPos, plotState);
                    break;
                }
            }
            return read;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static BlockState readFromPlot(Object sub, BlockPos absPos) {
        if (getPlotMethod == null || plotToLocalMethod == null || plotGetChunkHolderMethod == null || plotGetChunkMethod == null) {
            return null;
        }
        try {
            Object plot = getPlotMethod.invoke(sub);
            if (plot == null) {
                return null;
            }
            ChunkPos globalChunk = new ChunkPos(absPos.getX() >> 4, absPos.getZ() >> 4);
            Object localChunk = plotToLocalMethod.invoke(plot, globalChunk);
            if (!(localChunk instanceof ChunkPos lc)) {
                return null;
            }
            Object holder = plotGetChunkHolderMethod.invoke(plot, lc);
            if (holder == null) {
                return null;
            }
            Object chunkObj = plotGetChunkMethod.invoke(holder);
            if (chunkObj instanceof LevelChunk chunk) {
                BlockState state = chunk.getBlockState(absPos);
                if (state != null && !state.isAir()) {
                    return state;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
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