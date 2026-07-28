package com.xy2407.nsukaddition.common.cooking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 餐厅定义加载器，从外部目录和模组资源加载餐厅 JSON 定义。 */
@SuppressWarnings("null")
public final class RestaurantDefinitionLoader {

    private static final ConcurrentMap<String, LoadResult> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, LoadResult> EXT_BY_FILENAME = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, LoadResult> EXT_BY_ID = new ConcurrentHashMap<>();
    private static final String EXTERNAL_DIR = "xy2407_nsuk_addition/cooking";
    private static final String RESOURCE_PREFIX = "/data/xy2407_nsuk_addition/cooking/";

    private static final String VERSION_ENTRY = "_nsuk_version.txt";
    private static final String CURRENT_VERSION = "1";
    private static final List<String> COOKING_FILES = List.of(
            "restaurant.json", "restaurant.sk", "restaurant.nbt"
    );

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(RestaurantDefinition.class, new DefinitionDeserializer())
            .create();

    private RestaurantDefinitionLoader() {}

    /** 初始化：扫描外部目录。需在服务器启动时调用。 */
    public static void init() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        try {
            Files.createDirectories(dirPath);
            scanExternalDir();
        } catch (Exception ignored) {}
    }

    /** 将餐厅文件从模组资源部署到外部目录。 */
    public static void deployFiles() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("Failed to create cooking directory", e);
            return;
        }
        if (isUpToDate(dirPath)) return;

        for (String file : COOKING_FILES) {
            String resourcePath = RESOURCE_PREFIX + file;
            try (InputStream is = RestaurantDefinitionLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    NsukAddition.LOGGER.warn("Missing cooking resource: {}", resourcePath);
                    continue;
                }
                Path targetPath = dirPath.resolve(file);
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                NsukAddition.LOGGER.error("Failed to deploy cooking file: {}", file, e);
            }
        }
        writeVersion(dirPath);
        NsukAddition.LOGGER.info("Deployed cooking files to {}", dirPath);
        scanExternalDir();
    }

    private static boolean isUpToDate(Path dirPath) {
        Path versionFile = dirPath.resolve(VERSION_ENTRY);
        if (!Files.isRegularFile(versionFile)) return false;
        try {
            return CURRENT_VERSION.equals(Files.readString(versionFile, StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeVersion(Path dirPath) {
        try {
            Files.writeString(dirPath.resolve(VERSION_ENTRY), CURRENT_VERSION, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("Failed to write cooking version file", e);
        }
    }

    public static LoadResult loadForBuilding(PlacedBuildingRecord building) {
        if (building == null) return LoadResult.invalid("no_building");
        String cacheKey = building.category() + "/" + building.buildingFileName();
        return CACHE.computeIfAbsent(cacheKey, k -> loadForBuildingInternal(building));
    }

    private static LoadResult loadForBuildingInternal(PlacedBuildingRecord building) {
        String buildingId = stripExtension(building.buildingFileName());
        LoadResult external = findExternal(buildingId);
        if (external != null && external.valid()) return external;
        return loadFromModResources(buildingId);
    }

    private static void scanExternalDir() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        if (!Files.isDirectory(dirPath)) return;
        try (var stream = Files.newDirectoryStream(dirPath, "*.json")) {
            for (Path jsonFile : stream) {
                try {
                    String text = Files.readString(jsonFile, StandardCharsets.UTF_8);
                    String fileNameStem = stripExtension(jsonFile.getFileName().toString());
                    LoadResult result = parseJson(text, fileNameStem);
                    EXT_BY_FILENAME.put(fileNameStem.toLowerCase(Locale.ROOT), result);
                    if (result.definition() != null) {
                        EXT_BY_ID.put(result.definition().id().toLowerCase(Locale.ROOT), result);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static LoadResult findExternal(String buildingId) {
        String key = trimKey(buildingId);
        LoadResult result = EXT_BY_FILENAME.get(key);
        if (result != null && result.valid()) return result;
        result = EXT_BY_ID.get(key);
        if (result != null && result.valid()) return result;
        for (LoadResult candidate : EXT_BY_ID.values()) {
            if (!candidate.valid()) continue;
            String defKey = trimKey(candidate.definition().id());
            if (defKey.equals(key) || defKey.contains(key) || key.contains(defKey)) return candidate;
        }
        return null;
    }

    private static LoadResult loadFromModResources(String buildingId) {
        String resourcePath = RESOURCE_PREFIX + buildingId.toLowerCase(Locale.ROOT) + ".json";
        try (InputStream is = RestaurantDefinitionLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) return LoadResult.invalid("missing_cooking_json");
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parseJson(text, buildingId);
        } catch (Exception e) {
            return LoadResult.invalid("invalid_cooking_json");
        }
    }

    private static LoadResult parseJson(String text, String fallbackId) {
        try {
            RestaurantDefinition def = GSON.fromJson(text, RestaurantDefinition.class);
            if (def == null) return LoadResult.invalid("parse_failed");
            return LoadResult.valid(def);
        } catch (Exception e) {
            return LoadResult.invalid(e.getMessage());
        }
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName.toLowerCase(Locale.ROOT);
    }

    private static String trimKey(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    public static void clearCache() {
        CACHE.clear(); EXT_BY_FILENAME.clear(); EXT_BY_ID.clear();
        scanExternalDir();
    }

    public record LoadResult(RestaurantDefinition definition, boolean valid, List<String> errors) {
        public static LoadResult valid(RestaurantDefinition def) { return new LoadResult(def, true, List.of()); }
        public static LoadResult invalid(String error) { return new LoadResult(null, false, List.of(error)); }
    }

    private static final class DefinitionDeserializer implements JsonDeserializer<RestaurantDefinition> {
        @Override
        public RestaurantDefinition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String id = getString(obj, "id", "");
            String name = getString(obj, "name", id);
            RestaurantDefinition.JobDefinition job = obj.has("job") ? parseJob(obj.getAsJsonObject("job")) : null;
            Map<String, RestaurantDefinition.PointDefinition> points = parsePoints(obj);
            Map<String, RestaurantDefinition.ContainerDefinition> containers = parseContainers(obj);
            Map<String, Double> cookMap = parseCook(obj);
            List<String> cook = new ArrayList<>(cookMap.keySet());
            List<RestaurantDefinition.SeatDefinition> seats = parseSeats(obj);
            List<RestaurantDefinition.RecipeDefinition> recipes = cook.stream()
                    .map(itemId -> new RestaurantDefinition.RecipeDefinition(itemId, "", "",
                            List.of(),
                            List.of(new RestaurantDefinition.ItemRequirement(itemId, 1)),
                            200, List.of()))
                    .toList();
            return new RestaurantDefinition(id, name, job, points, containers, seats, cook, cookMap, recipes, null);
        }

        private RestaurantDefinition.JobDefinition parseJob(JsonObject j) {
            return new RestaurantDefinition.JobDefinition(
                    getString(j, "id", "chef"), getString(j, "name", "厨师"), getString(j, "heldItem", ""));
        }

        private Map<String, RestaurantDefinition.PointDefinition> parsePoints(JsonObject obj) {
            Map<String, RestaurantDefinition.PointDefinition> map = new HashMap<>();
            if (!obj.has("points")) return map;
            JsonObject pObj = obj.getAsJsonObject("points");
            for (String key : pObj.keySet()) {
                JsonObject p = pObj.getAsJsonObject(key);
                map.put(key, new RestaurantDefinition.PointDefinition(key,
                        getString(p, "type", "structure_pos"), parsePositions(p.get("positions"))));
            }
            return map;
        }

        private Map<String, RestaurantDefinition.ContainerDefinition> parseContainers(JsonObject obj) {
            Map<String, RestaurantDefinition.ContainerDefinition> map = new HashMap<>();
            if (!obj.has("containers")) return map;
            JsonObject cObj = obj.getAsJsonObject("containers");
            for (String key : cObj.keySet()) {
                JsonObject c = cObj.getAsJsonObject(key);
                map.put(key, new RestaurantDefinition.ContainerDefinition(key,
                        getString(c, "type", "structure_pos"), parsePositions(c.get("positions"))));
            }
            return map;
        }

        private List<RestaurantDefinition.SeatDefinition> parseSeats(JsonObject obj) {
            List<RestaurantDefinition.SeatDefinition> list = new ArrayList<>();
            if (!obj.has("seats")) return list;
            JsonArray arr = obj.getAsJsonArray("seats");
            for (JsonElement el : arr) {
                JsonObject s = el.getAsJsonObject();
                list.add(new RestaurantDefinition.SeatDefinition(parsePositions(s.get("positions"))));
            }
            return list;
        }

        /** 解析 cook 字段：菜品物品 id 列表及其价格。 */
        private Map<String, Double> parseCook(JsonObject obj) {
            if (!obj.has("cook")) return Map.of();
            Map<String, Double> map = new java.util.LinkedHashMap<>();
            for (JsonElement el : obj.getAsJsonArray("cook")) {
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    String item = getString(o, "item", "").trim();
                    double price = o.has("price") ? o.get("price").getAsDouble() : 30.0;
                    if (!item.isBlank()) map.put(item, price);
                } else {
                    String item = el.getAsString().trim();
                    if (!item.isBlank()) map.put(item, 30.0);
                }
            }
            return map;
        }

        private List<RestaurantDefinition.ItemRequirement> parseItems(JsonObject obj, String field) {
            List<RestaurantDefinition.ItemRequirement> items = new ArrayList<>();
            if (!obj.has(field)) return items;
            for (JsonElement el : obj.getAsJsonArray(field)) {
                JsonObject i = el.getAsJsonObject();
                items.add(new RestaurantDefinition.ItemRequirement(getString(i, "item", ""), getInt(i, "count", 1)));
            }
            return items;
        }

        private List<RestaurantDefinition.CookingStep> parseCookingSteps(JsonObject obj) {
            List<RestaurantDefinition.CookingStep> steps = new ArrayList<>();
            if (!obj.has("cookingSteps")) return steps;
            for (JsonElement el : obj.getAsJsonArray("cookingSteps")) {
                JsonObject s = el.getAsJsonObject();
                steps.add(new RestaurantDefinition.CookingStep(getString(s, "action", ""),
                        getString(s, "item", ""), getInt(s, "count", 1), getInt(s, "waitTicks", 0)));
            }
            return steps;
        }

        private List<BlockPos> parsePositions(JsonElement element) {
            if (element == null || !element.isJsonArray()) return List.of();
            List<BlockPos> positions = new ArrayList<>();
            for (JsonElement el : element.getAsJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (arr.size() >= 3) positions.add(new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()));
            }
            return positions;
        }

        private static String getString(JsonObject obj, String key, String def) {
            JsonElement el = obj.get(key);
            return el != null && !el.isJsonNull() ? el.getAsString() : def;
        }

        private static int getInt(JsonObject obj, String key, int def) {
            JsonElement el = obj.get(key);
            return el != null && !el.isJsonNull() ? el.getAsInt() : def;
        }
    }
}
