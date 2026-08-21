package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.storage.BuildingPoiIndex;
import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 为 PlacedBuildingService 的 O(n*m) 查找建立索引：
 * - findByPoi：原项目遍历所有建筑×所有 POI 做 String 比对，城市变大后每 tick 数十次调用造成 O(n*m) 卡顿。
 * - findByPoiPos：同理遍历所有建筑×所有 POI 做 BlockPos 比对。
 * 改为在不可变建筑列表上构建 POI 反向索引（key/worldPos → record），查询降为 O(1)。
 * 建筑列表本身是 List.copyOf 产生的不可变快照，register/unregister 会替换为新列表，
 * 旧列表无强引用后由 WeakHashMap 自动回收对应索引项。
 */
@Mixin(value = PlacedBuildingService.class, remap = false)
public class PlacedBuildingServiceIndexMixin {

    @Unique
    private static final Map<List<PlacedBuildingRecord>, BuildingPoiIndex> NSUK$INDEX_CACHE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    @Unique
    private static BuildingPoiIndex nsuk$index(List<PlacedBuildingRecord> buildings) {
        if (buildings == null || buildings.isEmpty()) {
            return BuildingPoiIndex.EMPTY;
        }
        synchronized (NSUK$INDEX_CACHE) {
            BuildingPoiIndex idx = NSUK$INDEX_CACHE.get(buildings);
            if (idx != null) {
                return idx;
            }
            idx = nsuk$buildIndex(buildings);
            NSUK$INDEX_CACHE.put(buildings, idx);
            return idx;
        }
    }

    @Unique
    private static BuildingPoiIndex nsuk$buildIndex(List<PlacedBuildingRecord> buildings) {
        if (buildings.isEmpty()) {
            return BuildingPoiIndex.EMPTY;
        }
        Map<String, PlacedBuildingRecord> byKey = new HashMap<>();
        Map<BlockPos, PlacedBuildingRecord> byPos = new HashMap<>();
        for (PlacedBuildingRecord record : buildings) {
            if (record == null || record.poiInstances() == null) {
                continue;
            }
            for (BuildingPoiInstance poi : record.poiInstances()) {
                if (poi == null) {
                    continue;
                }
                String key = poi.key();
                if (key != null) {
                    byKey.putIfAbsent(key.toLowerCase(Locale.ROOT), record);
                }
                BlockPos pos = poi.worldPos();
                if (pos != null) {
                    byPos.putIfAbsent(pos.immutable(), record);
                }
            }
        }
        return new BuildingPoiIndex(byKey, byPos);
    }

    @Inject(method = "findByPoi", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void nsuk$findByPoiIndexed(ServerLevel level, UUID poiId,
            CallbackInfoReturnable<PlacedBuildingRecord> cir) {
        if (level == null || poiId == null) {
            return;
        }
        List<PlacedBuildingRecord> buildings = PlacedBuildingService.getBuildings(level);
        BuildingPoiIndex idx = nsuk$index(buildings);
        cir.setReturnValue(idx.findByKey(poiId.toString().toLowerCase(Locale.ROOT)));
    }

    @Inject(method = "findByPoiPos", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void nsuk$findByPoiPosIndexed(ServerLevel level, BlockPos poiPos,
            CallbackInfoReturnable<PlacedBuildingRecord> cir) {
        if (level == null || poiPos == null) {
            return;
        }
        List<PlacedBuildingRecord> buildings = PlacedBuildingService.getBuildings(level);
        BuildingPoiIndex idx = nsuk$index(buildings);
        cir.setReturnValue(idx.findByPos(poiPos.immutable()));
    }
}
