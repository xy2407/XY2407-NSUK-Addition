package com.xy2407.nsukaddition.common.cooking;

import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;

import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 餐厅控制箱业务逻辑，处理界面构建、食谱选择、启停控制与厨师管理。 */
@SuppressWarnings("null")
public final class RestaurantControlBoxService {

    private RestaurantControlBoxService() {
    }

    public static RestaurantControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        RestaurantBoxData data = RestaurantBoxManager.get(level).getOrCreate(boxPos);
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        RestaurantDefinitionLoader.LoadResult loadResult = RestaurantDefinitionLoader.loadForBuilding(building);
        RestaurantDefinition definition = loadResult.definition();
        synchronizeBoxMetadata(level, data, building, definition);

        CitizenData worker = findAssignedWorker(level, boxPos);
        CitizenData waiter = findAssignedWorker(level, boxPos, RestaurantConstants.HIRE_ROLE_WAITER);
        String statusKey = resolveStatusKey(data, building, loadResult, worker);
        BuildingIntegrityService.IntegrityPreview integrity = BuildingIntegrityService.preview(level, building);
        List<RestaurantControlBoxView.RecipeEntry> recipes = definition == null ? List.of() : definition.recipes().stream()
                .map(RestaurantControlBoxService::recipeEntry)
                .toList();

        return new RestaurantControlBoxView(
                boxPos.immutable(),
                building != null,
                building != null ? building.displayName() : "",
                loadResult.valid(),
                definition != null ? definition.name() : "",
                statusKey,
                data.statusText(),
                data.running(),
                selectedRecipeId(data, definition),
                worker != null,
                worker != null ? worker.uuid() : null,
                worker != null ? worker.name() : "",
                waiter != null,
                waiter != null ? waiter.uuid() : null,
                waiter != null ? waiter.name() : "",
                building != null,
                building != null ? building.minPos().immutable() : BlockPos.ZERO,
                building != null ? building.maxPos().immutable() : BlockPos.ZERO,
                integrity.available(),
                integrity.percent(),
                integrity.repairableBlocks(),
                integrity.manualRepairBlocks(),
                integrity.repairCost(),
                pointMarkers(building, definition, boxPos),
                recipes,
                data.selectedCookItems(),
                AutoRestockConfig.isEnabled(boxPos)
        );
    }

    public static boolean selectRecipe(ServerLevel level, BlockPos boxPos, String recipeId) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.getOrCreate(boxPos);
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        RestaurantDefinition definition = RestaurantDefinitionLoader.loadForBuilding(building).definition();
        if (definition == null || definition.recipeById(recipeId) == null) {
            return false;
        }
        data.setSelectedRecipeId(recipeId);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(RestaurantConstants.STATUS_RECIPE_SELECTED);
        data.setStatusText("");
        manager.persist(data);
        return true;
    }

    public static boolean toggleRunning(ServerLevel level, BlockPos boxPos) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            data.setRunning(false);
            data.setProgressTicks(0);
            data.setCooldownTicks(0);
            data.setWorkState("");
            data.setStatusKey(RestaurantConstants.STATUS_PAUSED);
            data.setStatusText("");
            manager.persist(data);
            return true;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        RestaurantDefinitionLoader.LoadResult loadResult = RestaurantDefinitionLoader.loadForBuilding(building);
        RestaurantDefinition definition = loadResult.definition();
        CitizenData worker = findAssignedWorker(level, boxPos, RestaurantConstants.HIRE_ROLE_CHEF);
        if (worker == null) {
            setStatus(manager, data, RestaurantConstants.STATUS_NO_CHEF, "");
            return false;
        }
        if (!loadResult.valid()) {
            setStatus(manager, data, RestaurantConstants.STATUS_INVALID_DEFINITION, String.join(",", loadResult.errors()));
            return false;
        }
        String selectedRecipe = selectedRecipeId(data, definition);
        if (definition.recipeById(selectedRecipe) == null) {
            setStatus(manager, data, RestaurantConstants.STATUS_NO_RECIPE, "");
            return false;
        }
        synchronizeBoxMetadata(level, data, building, definition);
        data.setRunning(true);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(RestaurantConstants.STATUS_RUNNING);
        data.setStatusText("");
        manager.persist(data);
        return true;
    }

    public static void fireRole(ServerLevel level, BlockPos boxPos, String role) {
        CitizenData worker = findAssignedWorker(level, boxPos, role);
        if (worker != null) CitizenJobVisualService.clearMainHandOverride(worker.uuid());
        CitizenEmploymentService.fireAssigned(
                level,
                CitizenEmploymentService.workplaceId(RestaurantConstants.HIRE_SOURCE_TYPE, role, boxPos),
                RestaurantConstants.HIRE_SOURCE_TYPE, role, boxPos, "cooking_fired");
        RestaurantBoxData data = RestaurantBoxManager.get(level).getOrCreate(boxPos);
        if (RestaurantConstants.HIRE_ROLE_CHEF.equals(role)) {
            // 解雇厨师时停止运行，防止订单无人处理
            data.setRunning(false);
            data.setProgressTicks(0);
            data.setCooldownTicks(0);
            data.setWorkState("");
        }
        data.setStatusKey(RestaurantConstants.STATUS_IDLE); data.setStatusText("");
        RestaurantBoxManager.get(level).persist(data);
    }

    public static void fireWorker(ServerLevel level, BlockPos boxPos) {
        // 解雇所有角色（厨师+服务员）
        for (String role : new String[]{RestaurantConstants.HIRE_ROLE_CHEF, RestaurantConstants.HIRE_ROLE_WAITER}) {
            CitizenData worker = findAssignedWorker(level, boxPos, role);
            if (worker != null) CitizenJobVisualService.clearMainHandOverride(worker.uuid());
            CitizenEmploymentService.fireAssigned(
                    level,
                    CitizenEmploymentService.workplaceId(RestaurantConstants.HIRE_SOURCE_TYPE, role, boxPos),
                    RestaurantConstants.HIRE_SOURCE_TYPE, role, boxPos, "cooking_fired");
        }
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.getOrCreate(boxPos);
        data.setRunning(false); data.setProgressTicks(0); data.setCooldownTicks(0);
        data.setWorkState(""); data.setStatusKey(RestaurantConstants.STATUS_WORKER_FIRED); data.setStatusText("");
        manager.persist(data);
    }

    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) return;
        fireWorker(level, boxPos);
        com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig.remove(level, boxPos);
        RestaurantBoxManager.get(level).remove(boxPos);
    }

    public static void interrupt(ServerLevel level, UUID citizenId, String reason) {
        if (level == null || citizenId == null) return;
        for (RestaurantBoxData data : RestaurantBoxManager.get(level).all()) {
            UUID assigned = CitizenService.findAssignedCitizen(level, CitizenEmploymentService.workplaceId(RestaurantConstants.HIRE_SOURCE_TYPE, RestaurantConstants.HIRE_ROLE_CHEF, data.boxPos()));
            if (!citizenId.equals(assigned)) continue;
            data.setRunning(false);
            data.setProgressTicks(0);
            data.setCooldownTicks(0);
            data.setWorkState("");
            data.setStatusKey(RestaurantConstants.STATUS_INTERRUPTED);
            data.setStatusText(reason != null ? reason : "");
            RestaurantBoxManager.get(level).persist(data);
        }
    }

    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        return PlacedBuildingService.findByContainedPosAndCategory(level, boxPos,
                RestaurantConstants.BUILDING_CATEGORY, "industry", "industrial");
    }

    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos, String role) {
        return CitizenEmploymentService.findAssigned(level, RestaurantConstants.HIRE_SOURCE_TYPE, role, boxPos)
                .orElse(null);
    }

    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos) {
        return findAssignedWorker(level, boxPos, RestaurantConstants.HIRE_ROLE_CHEF);
    }

    static void synchronizeBoxMetadata(ServerLevel level, RestaurantBoxData data, PlacedBuildingRecord building, RestaurantDefinition definition) {
        if (data == null) return;
        boolean changed = false;
        if (building != null && !building.buildingId().toString().equals(data.buildingId())) {
            data.setBuildingId(building.buildingId().toString());
            changed = true;
        }
        if (definition != null) {
            if (!definition.id().equals(data.definitionId())) {
                data.setDefinitionId(definition.id());
                changed = true;
            }
            if (data.selectedRecipeId().isBlank() || definition.recipeById(data.selectedRecipeId()) == null) {
                data.setSelectedRecipeId(definition.defaultRecipeId());
                changed = true;
            }
        }
        if (changed && level != null) {
            RestaurantBoxManager.get(level).persist(data);
        }
    }

    private static String resolveStatusKey(RestaurantBoxData data, PlacedBuildingRecord building, RestaurantDefinitionLoader.LoadResult loadResult, CitizenData worker) {
        if (building == null) return RestaurantConstants.STATUS_NO_BUILDING;
        if (!loadResult.valid()) return RestaurantConstants.STATUS_INVALID_DEFINITION;
        if (worker == null) return RestaurantConstants.STATUS_NO_CHEF;
        if (!data.statusKey().isBlank()) return data.statusKey();
        return data.running() ? RestaurantConstants.STATUS_RUNNING : RestaurantConstants.STATUS_IDLE;
    }

    private static RestaurantControlBoxView.RecipeEntry recipeEntry(RestaurantDefinition.RecipeDefinition recipe) {
        List<RestaurantControlBoxView.ItemEntry> inputs = recipe.inputItems().stream()
                .map(i -> new RestaurantControlBoxView.ItemEntry(i.itemId(), i.count()))
                .toList();
        List<RestaurantControlBoxView.ItemEntry> outputs = recipe.resultItems().stream()
                .map(i -> new RestaurantControlBoxView.ItemEntry(i.itemId(), i.count()))
                .toList();
        return new RestaurantControlBoxView.RecipeEntry(
                recipe.id(), recipe.name(), List.copyOf(inputs), List.copyOf(outputs));
    }

    private static List<RestaurantControlBoxView.PointMarker> pointMarkers(PlacedBuildingRecord building,
                                                                            RestaurantDefinition definition, BlockPos boxPos) {
        if (building == null || definition == null || boxPos == null) return List.of();
        List<RestaurantControlBoxView.PointMarker> markers = new ArrayList<>();
        int colorWork = 0xAA33CCFF;
        int colorContainer = 0xAAFF9900;
        int rotation = rotationDegrees(building.facing());
        for (RestaurantDefinition.PointDefinition point : definition.points().values()) {
            if (point == null) continue;
            List<BlockPos> positions = resolveMarkerPositions(building, point.type(), point.positions(), boxPos, rotation);
            for (BlockPos pos : positions) {
                markers.add(new RestaurantControlBoxView.PointMarker(point.id(), "point", pos, colorWork));
            }
        }
        for (RestaurantDefinition.ContainerDefinition container : definition.containers().values()) {
            if (container == null) continue;
            List<BlockPos> positions = resolveMarkerPositions(building, container.type(), container.positions(), boxPos, rotation);
            for (BlockPos pos : positions) {
                markers.add(new RestaurantControlBoxView.PointMarker(container.id(), "container", pos, colorContainer));
            }
        }
        return List.copyOf(markers);
    }

    private static List<BlockPos> resolveMarkerPositions(PlacedBuildingRecord building, String type,
                                                         List<BlockPos> positions, BlockPos boxPos, int rotation) {
        if ("control_box_relative".equalsIgnoreCase(type)) {
            List<BlockPos> result = new ArrayList<>(positions.size());
            for (BlockPos offset : positions) {
                if (offset == null) continue;
                result.add(boxPos.offset(BuildingTransform.rotatePosition(offset, rotation)).immutable());
            }
            return List.copyOf(result);
        }
        if ("structure_pos".equalsIgnoreCase(type)) {
            return IndustrialCoordinateResolver.resolvePositions(building, positions);
        }
        return List.of();
    }

    private static int rotationDegrees(String facing) {
        String normalized = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    private static String selectedRecipeId(RestaurantBoxData data, RestaurantDefinition definition) {
        if (definition == null) return data.selectedRecipeId();
        RestaurantDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        return recipe != null ? recipe.id() : definition.defaultRecipeId();
    }

    private static void setStatus(RestaurantBoxManager manager, RestaurantBoxData data, String statusKey, String statusText) {
        data.setRunning(false);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(statusKey);
        data.setStatusText(statusText);
        manager.persist(data);
    }
}
