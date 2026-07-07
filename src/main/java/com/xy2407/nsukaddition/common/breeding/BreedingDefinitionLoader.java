package com.xy2407.nsukaddition.common.breeding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 繁殖定义加载器，从建筑包和外部 JSON 文件加载并缓存繁殖定义。 */
public final class BreedingDefinitionLoader {
    private static final int MAX_POSITIONS = 64;
    private static final int MAX_RECIPES = 256;
    private static final ConcurrentHashMap<String, LoadResult> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LoadResult> EXT_BY_FILENAME = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LoadResult> EXT_BY_ID = new ConcurrentHashMap<>();
    private static final String EXTERNAL_DIR = "xy2407_nsuk_addition/breeding";

    private BreedingDefinitionLoader() {
    }

    public static void init() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        try {
            Files.createDirectories(dirPath);
            scanExternalDir(dirPath);
        } catch (Exception ignored) {
        }
    }

    public static void clearCache() {
        CACHE.clear();
        EXT_BY_FILENAME.clear();
        EXT_BY_ID.clear();
    }

    private static void scanExternalDir(Path dirPath) {
        try (var stream = Files.newDirectoryStream(dirPath, "*.json")) {
            for (Path jsonFile : stream) {
                try {
                    String text = Files.readString(jsonFile, StandardCharsets.UTF_8);
                    String fileNameStem = stripExtension(jsonFile.getFileName().toString());
                    LoadResult result = loadText(text, fileNameStem, jsonFile);
                    EXT_BY_FILENAME.put(fileNameStem.toLowerCase(Locale.ROOT), result);
                    if (result.definition() != null) {
                        EXT_BY_ID.put(result.definition().id().toLowerCase(Locale.ROOT), result);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static LoadResult loadForBuilding(PlacedBuildingRecord building) {
        if (building == null) {
            return LoadResult.missing("missing_building");
        }
        String cacheKey = building.category() + "/" + building.buildingFileName();
        return CACHE.computeIfAbsent(cacheKey, k -> loadForBuildingInternal(building));
    }

    public static LoadResult load(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            return loadText(text, stripExtension(path.getFileName().toString()), path);
        } catch (Exception e) {
            return LoadResult.missing("invalid_breeding_json");
        }
    }

    private static LoadResult loadForBuildingInternal(PlacedBuildingRecord building) {
        String buildingId;
        String category = building.category();
        String fileName = building.buildingFileName();

        Optional<BuildingCatalog.BuildingDefinition> definition = BuildingCatalog.findBuilding(category, fileName);
        if (definition.isPresent()) {
            buildingId = stripExtension(definition.get().metaFileName());
            String breedingFile = resolveBreedingFileName(definition.get());
            if (breedingFile != null) {
                return load(definition.get(), breedingFile);
            }
        } else {
            buildingId = stripExtension(fileName);
        }

        LoadResult external = findExternal(buildingId);
        if (external != null && external.definition() != null) {
            return external;
        }
        return loadFromModResources(buildingId);
    }

    private static LoadResult findExternal(String buildingId) {
        String key = trimKey(buildingId);
        LoadResult result = EXT_BY_FILENAME.get(key);
        if (result != null && result.valid()) {
            return result;
        }
        result = EXT_BY_ID.get(key);
        if (result != null && result.valid()) {
            return result;
        }
        for (LoadResult candidate : EXT_BY_ID.values()) {
            if (!candidate.valid()) {
                continue;
            }
            String defKey = trimKey(candidate.definition().id());
            if (defKey.equals(key) || defKey.contains(key) || key.contains(defKey)) {
                return candidate;
            }
        }
        return null;
    }

    private static String trimKey(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    private static String resolveBreedingFileName(BuildingCatalog.BuildingDefinition definition) {
        String explicit = explicitBreedingFileName(definition);
        if (explicit != null) {
            return explicit;
        }
        String sibling = stripExtension(definition.metaFileName()) + ".json";
        return definition.hasFile(sibling) ? definition.actualFileName(sibling) : null;
    }

    private static String explicitBreedingFileName(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return null;
        }
        try {
            String text = definition.readFileText(definition.metaFileName()).orElse("");
            for (String rawLine : text.split("\\R")) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (!line.regionMatches(true, 0, "breeding:", 0, "breeding:".length())) {
                    continue;
                }
                String fileName = line.substring("breeding:".length()).trim();
                if (!fileName.isBlank() && definition.hasFile(fileName)) {
                    return definition.actualFileName(fileName);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static LoadResult load(BuildingCatalog.BuildingDefinition definition, String fileName) {
        String key = "pkg:" + definition.packageKey() + ":" + definition.category() + "/" + fileName.toLowerCase(Locale.ROOT);
        LoadResult cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            String text = definition.readFileText(fileName).orElse(null);
            if (text == null) {
                return LoadResult.missing("missing_breeding_json");
            }
            LoadResult result = loadText(text, stripExtension(fileName), definition.packagePath());
            CACHE.put(key, result);
            return result;
        } catch (Exception e) {
            return LoadResult.missing("invalid_breeding_json");
        }
    }

    private static LoadResult loadFromModResources(String buildingId) {
        String resourcePath = "/data/xy2407_nsuk_addition/breeding/" + buildingId.toLowerCase(Locale.ROOT) + ".json";
        try (InputStream is = BreedingDefinitionLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return LoadResult.missing("missing_breeding_json");
            }
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return loadText(text, buildingId, null);
        } catch (Exception e) {
            return LoadResult.missing("invalid_breeding_json");
        }
    }

    private static LoadResult loadText(String text, String fallbackId, Path path) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        List<String> errors = new ArrayList<>();
        String id = string(root, "id", fallbackId);
        String name = string(root, "name", id);
        String jobType = stringAny(root, "breeder", "breeding_worker", "jobType", "JobType", "job_type");
        String jobName = stringAny(root, name, "jobName", "JobName", "job_name");
        String heldItem = string(root, "heldItem", "");
        Map<String, BreedingDefinition.PointDefinition> points = parsePoints(root.getAsJsonObject("points"), errors);
        Map<String, BreedingDefinition.ContainerDefinition> containers = parseContainers(root.getAsJsonObject("containers"), errors);
        List<BreedingDefinition.RecipeDefinition> recipes = parseRecipes(root.getAsJsonArray("recipes"), errors);
        if (recipes.isEmpty()) {
            errors.add("missing_recipes");
        }
        BreedingDefinition definition = new BreedingDefinition(
                id,
                name,
                jobType,
                jobName,
                heldItem,
                Map.copyOf(points),
                Map.copyOf(containers),
                List.copyOf(recipes),
                path
        );
        return new LoadResult(definition, List.copyOf(errors), path);
    }

    private static Map<String, BreedingDefinition.PointDefinition> parsePoints(JsonObject object, List<String> errors) {
        Map<String, BreedingDefinition.PointDefinition> points = new LinkedHashMap<>();
        if (object == null) {
            return points;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonObject pointObject = asObject(entry.getValue());
            if (pointObject == null) {
                errors.add("invalid_point:" + id);
                continue;
            }
            List<BlockPos> positions = parsePositions(pointObject, errors, "point:" + id);
            if (positions.isEmpty()) {
                continue;
            }
            points.put(id, new BreedingDefinition.PointDefinition(id, string(pointObject, "type", "control_box_relative"), positions));
        }
        return points;
    }

    private static Map<String, BreedingDefinition.ContainerDefinition> parseContainers(JsonObject object, List<String> errors) {
        Map<String, BreedingDefinition.ContainerDefinition> containers = new LinkedHashMap<>();
        if (object == null) {
            return containers;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonObject containerObject = asObject(entry.getValue());
            if (containerObject == null) {
                errors.add("invalid_container:" + id);
                continue;
            }
            List<BlockPos> positions = parsePositions(containerObject, errors, "container:" + id);
            if (positions.isEmpty()) {
                continue;
            }
            containers.put(id, new BreedingDefinition.ContainerDefinition(id, string(containerObject, "type", "structure_pos"), positions));
        }
        return containers;
    }

    private static List<BreedingDefinition.RecipeDefinition> parseRecipes(JsonArray array, List<String> errors) {
        List<BreedingDefinition.RecipeDefinition> recipes = new ArrayList<>();
        if (array == null) {
            return recipes;
        }
        int limit = Math.min(array.size(), MAX_RECIPES);
        for (int i = 0; i < limit; i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                errors.add("invalid_recipe:" + i);
                continue;
            }
            String id = string(object, "id", "recipe_" + i);
            String recipeName = string(object, "name", id);
            String typeRaw = stringAny(object, "breeding_slaughter", "type", "recipeType", "kind");
            BreedingDefinition.RecipeType type = BreedingDefinition.RecipeType.fromString(typeRaw);
            String entityType = stringAny(object, "", "entityType", "entity", "animal", "targetEntity");
            String feedItem = stringAny(object, "", "feedItem", "foodItem", "inputItem", "item");
            String heldItem = string(object, "heldItem", "");
            if (entityType.isBlank()) {
                errors.add("missing_entity_type:recipe:" + id);
                continue;
            }
            if (feedItem.isBlank()) {
                errors.add("missing_feed_item:recipe:" + id);
                continue;
            }
            recipes.add(new BreedingDefinition.RecipeDefinition(id, recipeName, type, entityType, feedItem, heldItem));
        }
        return recipes;
    }

    private static List<BlockPos> parsePositions(JsonObject object, List<String> errors, String context) {
        List<BlockPos> positions = new ArrayList<>();
        if (object.has("pos")) {
            BlockPos pos = parsePositionArray(object.get("pos"));
            if (pos != null) {
                positions.add(pos);
            }
        }
        if (object.has("positions") && object.get("positions").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("positions");
            int limit = Math.min(array.size(), MAX_POSITIONS);
            for (int i = 0; i < limit; i++) {
                BlockPos pos = parsePositionArray(array.get(i));
                if (pos != null) {
                    positions.add(pos);
                }
            }
        }
        if (positions.isEmpty()) {
            errors.add("missing_positions:" + context);
        }
        return List.copyOf(positions);
    }

    private static BlockPos parsePositionArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String stringAny(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            String value = string(object, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName.toLowerCase(Locale.ROOT);
    }

    public record LoadResult(BreedingDefinition definition, List<String> errors, Path path) {
        public static LoadResult missing(String error) {
            return new LoadResult(null, List.of(error), null);
        }

        public boolean valid() {
            return definition != null && errors.isEmpty();
        }
    }
}
