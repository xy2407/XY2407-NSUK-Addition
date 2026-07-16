package com.xy2407.nsukaddition.common.breeding;

import common.cn.kafei.simukraft.building.BuildingCatalog;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import com.xy2407.nsukaddition.NsukAddition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 繁殖控制箱业务逻辑，处理界面构建、配方选择、启停控制与工人管理。 */
@SuppressWarnings("null")
public final class BreedingControlBoxService {

    private BreedingControlBoxService() {
    }

    public static BreedingControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        BreedingBoxData data = BreedingBoxManager.get(level).getOrCreate(boxPos);
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
        BreedingDefinition definition = loadResult.definition();
        synchronizeBoxMetadata(level, data, building, definition);

        CitizenData worker = findAssignedWorker(level, boxPos);
        String statusKey = resolveStatusKey(data, building, loadResult, worker);
        BuildingIntegrityService.IntegrityPreview integrity = BuildingIntegrityService.preview(level, building);
        List<BreedingControlBoxView.RecipeEntry> recipes = definition == null ? List.of() : definition.recipes().stream()
                .map(BreedingControlBoxService::recipeEntry)
                .toList();

        return new BreedingControlBoxView(
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
                building != null,
                building != null ? building.minPos().immutable() : BlockPos.ZERO,
                building != null ? building.maxPos().immutable() : BlockPos.ZERO,
                integrity.available(),
                integrity.percent(),
                integrity.repairableBlocks(),
                integrity.manualRepairBlocks(),
                integrity.repairCost(),
                pointMarkers(building, definition, boxPos),
                recipes
        );
    }

    public static boolean selectRecipe(ServerLevel level, BlockPos boxPos, String recipeId) {
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.getOrCreate(boxPos);
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        BreedingDefinition definition = BreedingDefinitionLoader.loadForBuilding(building).definition();
        if (definition == null || definition.recipeById(recipeId) == null) {
            return false;
        }
        data.setSelectedRecipeId(recipeId);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(BreedingConstants.STATUS_RECIPE_SELECTED);
        data.setStatusText("");
        manager.persist(data);
        return true;
    }

    public static boolean toggleRunning(ServerLevel level, BlockPos boxPos) {
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            data.setRunning(false);
            data.setProgressTicks(0);
            data.setCooldownTicks(0);
            data.setWorkState("");
            data.setStatusKey(BreedingConstants.STATUS_PAUSED);
            data.setStatusText("");
            manager.persist(data);
            return true;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
        BreedingDefinition definition = loadResult.definition();
        CitizenData worker = findAssignedWorker(level, boxPos);
        if (building == null) {
            setStatus(manager, data, BreedingConstants.STATUS_NO_BUILDING, "");
            return false;
        }
        if (!loadResult.valid()) {
            setStatus(manager, data, BreedingConstants.STATUS_INVALID_DEFINITION, String.join(",", loadResult.errors()));
            return false;
        }
        if (worker == null) {
            setStatus(manager, data, BreedingConstants.STATUS_NO_WORKER, "");
            return false;
        }
        String selectedRecipe = selectedRecipeId(data, definition);
        if (definition.recipeById(selectedRecipe) == null) {
            setStatus(manager, data, BreedingConstants.STATUS_NO_RECIPE, "");
            return false;
        }
        synchronizeBoxMetadata(level, data, building, definition);
        data.setRunning(true);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(BreedingConstants.STATUS_RUNNING);
        data.setStatusText("");
        manager.persist(data);
        return true;
    }

    public static void fireWorker(ServerLevel level, BlockPos boxPos) {
        CitizenData worker = findAssignedWorker(level, boxPos);
        if (worker != null) {
            CitizenJobVisualService.clearMainHandOverride(worker.uuid());
        }
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        BreedingBoxData data = manager.getOrCreate(boxPos);
        data.setRunning(false);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(BreedingConstants.STATUS_WORKER_FIRED);
        data.setStatusText("");
        manager.persist(data);
        CitizenEmploymentService.fireAssigned(
                level,
                CitizenEmploymentService.workplaceId(BreedingConstants.HIRE_SOURCE_TYPE, BreedingConstants.HIRE_ROLE, boxPos),
                BreedingConstants.HIRE_SOURCE_TYPE,
                BreedingConstants.HIRE_ROLE,
                boxPos,
                "breeding_fired");
    }

    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) return;
        fireWorker(level, boxPos);
        com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig.remove(level, boxPos);
        BreedingBoxManager.get(level).remove(boxPos);
    }

    public static void interrupt(ServerLevel level, UUID citizenId, String reason) {
        if (level == null || citizenId == null) return;
        for (BreedingBoxData data : BreedingBoxManager.get(level).all()) {
            UUID assigned = CitizenService.findAssignedCitizen(level, CitizenEmploymentService.workplaceId(BreedingConstants.HIRE_SOURCE_TYPE, BreedingConstants.HIRE_ROLE, data.boxPos()));
            if (!citizenId.equals(assigned)) continue;
            data.setRunning(false);
            data.setProgressTicks(0);
            data.setCooldownTicks(0);
            data.setWorkState("");
            data.setStatusKey(BreedingConstants.STATUS_INTERRUPTED);
            data.setStatusText(reason != null ? reason : "");
            BreedingBoxManager.get(level).persist(data);
        }
    }

    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        return PlacedBuildingService.findByContainedPosAndCategory(level, boxPos,
                BreedingConstants.BUILDING_CATEGORY, "industry", "industrial");
    }

    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, BreedingConstants.HIRE_SOURCE_TYPE, BreedingConstants.HIRE_ROLE, boxPos)
                .orElse(null);
    }

    static void synchronizeBoxMetadata(ServerLevel level, BreedingBoxData data, PlacedBuildingRecord building, BreedingDefinition definition) {
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
            BreedingBoxManager.get(level).persist(data);
        }
    }

    private static String resolveStatusKey(BreedingBoxData data, PlacedBuildingRecord building, BreedingDefinitionLoader.LoadResult loadResult, CitizenData worker) {
        if (building == null) return BreedingConstants.STATUS_NO_BUILDING;
        if (!loadResult.valid()) return BreedingConstants.STATUS_INVALID_DEFINITION;
        if (worker == null) return BreedingConstants.STATUS_NO_WORKER;
        if (!data.statusKey().isBlank()) return data.statusKey();
        return data.running() ? BreedingConstants.STATUS_RUNNING : BreedingConstants.STATUS_IDLE;
    }

    private static BreedingControlBoxView.RecipeEntry recipeEntry(BreedingDefinition.RecipeDefinition recipe) {
        List<BreedingControlBoxView.ItemEntry> inputs = List.of(
                new BreedingControlBoxView.ItemEntry(recipe.effectiveFeedItem(), 1)
        );
        List<BreedingControlBoxView.ItemEntry> outputs = recipeOutputEntries(recipe);
        return new BreedingControlBoxView.RecipeEntry(
                recipe.id(), recipe.name(), List.copyOf(inputs), List.copyOf(outputs));
    }

    private static List<BreedingControlBoxView.ItemEntry> recipeOutputEntries(BreedingDefinition.RecipeDefinition recipe) {
        String entityId = recipe.targetEntityType();
        if (entityId.isBlank()) {
            return List.of();
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            return List.of();
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (entityType == null) {
            return List.of();
        }
        Item displayItem = SpawnEggItem.byId(entityType);
        if (displayItem == null || displayItem == Items.AIR) {
            ResourceLocation bucketId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_bucket");
            displayItem = BuiltInRegistries.ITEM.get(bucketId);
        }
        if (displayItem == null || displayItem == Items.AIR) {
            return List.of();
        }
        return List.of(new BreedingControlBoxView.ItemEntry(BuiltInRegistries.ITEM.getKey(displayItem).toString(), 1));
    }

    private static List<BreedingControlBoxView.PointMarker> pointMarkers(PlacedBuildingRecord building,
                                                                         BreedingDefinition definition, BlockPos boxPos) {
        if (building == null || definition == null || boxPos == null) return List.of();
        List<BreedingControlBoxView.PointMarker> markers = new ArrayList<>();
        int colorWork = 0xAA33CCFF;
        int colorFeed = 0xAA00FF66;
        int colorContainer = 0xAAFF9900;
        int rotation = rotationDegrees(building.facing());
        for (BreedingDefinition.PointDefinition point : definition.points().values()) {
            if (point == null) continue;
            List<BlockPos> positions = resolveMarkerPositions(building, point.type(), point.positions(), boxPos, rotation);
            if (positions.isEmpty()) continue;
            int color = point.id().toLowerCase().contains("feed") ? colorFeed : colorWork;
            for (BlockPos pos : positions) {
                markers.add(new BreedingControlBoxView.PointMarker(point.id(), "point", pos, color));
            }
        }
        for (BreedingDefinition.ContainerDefinition container : definition.containers().values()) {
            if (container == null) continue;
            List<BlockPos> positions = resolveMarkerPositions(building, container.type(), container.positions(), boxPos, rotation);
            for (BlockPos pos : positions) {
                markers.add(new BreedingControlBoxView.PointMarker(container.id(), "container", pos, colorContainer));
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

    private static String selectedRecipeId(BreedingBoxData data, BreedingDefinition definition) {
        if (definition == null) return data.selectedRecipeId();
        BreedingDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        return recipe != null ? recipe.id() : definition.defaultRecipeId();
    }

    private static void setStatus(BreedingBoxManager manager, BreedingBoxData data, String statusKey, String statusText) {
        data.setRunning(false);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(statusKey);
        data.setStatusText(statusText);
        manager.persist(data);
    }
}
