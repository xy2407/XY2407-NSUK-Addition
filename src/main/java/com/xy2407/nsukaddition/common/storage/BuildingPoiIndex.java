package com.xy2407.nsukaddition.common.storage;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 已放置建筑的 POI 反向索引，供 PlacedBuildingServiceIndexMixin 使用。
 * 独立放在 common 包而非 Mixin 内部类，避免目标类合并后类加载器无法访问。
 */
public final class BuildingPoiIndex {

    public static final BuildingPoiIndex EMPTY = new BuildingPoiIndex(Map.of(), Map.of());

    private final Map<String, PlacedBuildingRecord> byKey;
    private final Map<BlockPos, PlacedBuildingRecord> byPos;

    public BuildingPoiIndex(Map<String, PlacedBuildingRecord> byKey, Map<BlockPos, PlacedBuildingRecord> byPos) {
        this.byKey = byKey;
        this.byPos = byPos;
    }

    public PlacedBuildingRecord findByKey(String poiKeyLower) {
        return byKey.get(poiKeyLower);
    }

    public PlacedBuildingRecord findByPos(BlockPos poiPos) {
        return byPos.get(poiPos);
    }
}
