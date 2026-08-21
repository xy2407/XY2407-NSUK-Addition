package com.xy2407.nsukaddition.common.cooking;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 餐厅控制箱界面数据，包含建筑信息、食谱列表、工人状态与标记点。 */
@SuppressWarnings("null")
public record RestaurantControlBoxView(BlockPos boxPos,
                                       boolean hasBuilding,
                                       String buildingName,
                                       boolean definitionValid,
                                       String definitionName,
                                       String statusKey,
                                       String statusText,
                                       boolean running,
                                       String selectedRecipeId,
                                       boolean hasWorker,
                                       UUID workerId,
                                       String workerName,
                                       boolean hasWaiter,
                                       UUID waiterId,
                                       String waiterName,
                                       boolean hasBuildingBounds,
                                       BlockPos boundsMin,
                                       BlockPos boundsMax,
                                       boolean integrityAvailable,
                                       double integrityPercent,
                                       int integrityRepairableBlocks,
                                       int integrityManualRepairBlocks,
                                       double integrityRepairCost,
                                       List<PointMarker> pointMarkers,
                                       List<RecipeEntry> recipes,
                                       Set<String> selectedCookItems,
                                       boolean autoRestock,
                                       String waiterType,
                                       List<MaidWaiter> maidWaiters) {

    public record MaidWaiter(UUID uuid, String name) {
    }

    public record RecipeEntry(String id, String name, List<ItemEntry> inputs, List<ItemEntry> outputs) {
    }

    public record ItemEntry(String itemId, String potionId, int count, String connector, String itemSpec) {
        public ItemEntry(String itemId, int count) {
            this(itemId, "", count, "", "");
        }
    }

    public record PointMarker(String id, String kind, BlockPos pos, int color) {
    }
}
