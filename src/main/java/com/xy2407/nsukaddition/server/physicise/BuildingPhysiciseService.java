package com.xy2407.nsukaddition.server.physicise;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** 物理化建筑装配服务：通过反射调用 sable 仅在其存在时将物理化分类建筑组装为可移动子世界。 */
public final class BuildingPhysiciseService {

    private BuildingPhysiciseService() {
    }

    /** 物理化建筑分类标识。 */
    public static final String CATEGORY_PHYSICAL = "physical";

    private static volatile Boolean SABLE_AVAILABLE;
    private static Method ASSEMBLE_METHOD;
    private static Constructor<?> BBOX_CONSTRUCTOR;

    public static void onBuildingRegistered(ServerLevel level, PlacedBuildingRecord record) {
        if (level == null || record == null || record.category() == null) {
            return;
        }
        if (!CATEGORY_PHYSICAL.equalsIgnoreCase(record.category())) {
            return;
        }
        BlockPos min = record.minPos();
        BlockPos max = record.maxPos();
        BlockPos origin = record.worldOrigin();
        if (min == null || max == null || origin == null) {
            return;
        }
        if (!initSable()) {
            return;
        }
        try {
            List<BlockPos> blocks = collectWorldBlocks(level, min, max);
            if (blocks.isEmpty()) {
                return;
            }
            // 透视 bbox 用真实包围盒，遍历世界读实际方块（含朝向旋转结果并跳过空气）
            Object bounds = BBOX_CONSTRUCTOR.newInstance(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            ASSEMBLE_METHOD.invoke(null, level, origin, blocks, bounds);
        } catch (ReflectiveOperationException | RuntimeException e) {
            NsukAddition.LOGGER.warn("物理化建筑装配失败 category={} origin={}", record.category(), origin, e);
        }
    }

    /** 探测 sable 可用性并缓存反射句柄，未安装 sable 时静默跳过物理化。 */
    private static synchronized boolean initSable() {
        if (SABLE_AVAILABLE != null) {
            return SABLE_AVAILABLE;
        }
        try {
            Class<?> helper = Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
            Class<?> boundsInterface = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Class<?> boundsImpl = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i");
            ASSEMBLE_METHOD = helper.getMethod("assembleBlocks", ServerLevel.class, BlockPos.class, Iterable.class, boundsInterface);
            BBOX_CONSTRUCTOR = boundsImpl.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class);
            SABLE_AVAILABLE = true;
        } catch (Throwable t) {
            SABLE_AVAILABLE = false;
        }
        return SABLE_AVAILABLE;
    }

    private static List<BlockPos> collectWorldBlocks(ServerLevel level, BlockPos min, BlockPos max) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    result.add(pos);
                }
            }
        }
        return result;
    }
}