package com.xy2407.nsukaddition.common.foreigntrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外贸分类配置:基准分类价值(category_base.json)与村庄可售分类映射(village_categories.json)。
 */
public final class ForeignTradeCategoryConfig {

    private static final Path CONFIG_DIR = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition");
    private static final Path BASE_FILE = CONFIG_DIR.resolve("category_base.json");
    private static final String BASE_RESOURCE = "/data/xy2407_nsuk_addition/trade/category_base.json";
    private static final Path VILLAGE_FILE = CONFIG_DIR.resolve("village_categories.json");
    private static final String VILLAGE_RESOURCE = "/data/xy2407_nsuk_addition/trade/village_categories.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Map<String, Double> categoryBase = new HashMap<>();
    private static Map<String, List<String>> villageCategories = new HashMap<>();

    private ForeignTradeCategoryConfig() {
    }

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            syncFromResource(BASE_FILE, BASE_RESOURCE);
            syncFromResource(VILLAGE_FILE, VILLAGE_RESOURCE);
            load();
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to init foreign trade category config", e);
            categoryBase = new HashMap<>();
            villageCategories = new HashMap<>();
        }
    }

    private static void syncFromResource(Path file, String resource) {
        try {
            byte[] resourceBytes;
            try (var is = ForeignTradeCategoryConfig.class.getResourceAsStream(resource)) {
                if (is == null) {
                    NsukAddition.LOGGER.warn("Resource {} not found, skipping sync", resource);
                    return;
                }
                resourceBytes = is.readAllBytes();
            }
            byte[] diskBytes = null;
            if (Files.exists(file)) {
                diskBytes = Files.readAllBytes(file);
            }
            if (diskBytes == null || !java.util.Arrays.equals(resourceBytes, diskBytes)) {
                Files.write(file, resourceBytes);
                NsukAddition.LOGGER.info("Synced {} from resources to {}", resource, file);
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to sync config {} from resources", resource, e);
        }
    }

    private static void load() {
        try {
            String baseText = Files.readString(BASE_FILE, StandardCharsets.UTF_8);
            JsonObject base = GSON.fromJson(baseText, JsonObject.class);
            Map<String, Double> baseMap = new HashMap<>();
            JsonObject categories = base != null ? base.getAsJsonObject("category_base") : null;
            if (categories != null) {
                for (String key : categories.keySet()) {
                    baseMap.put(key, categories.get(key).getAsDouble());
                }
            }
            categoryBase = baseMap;

            String villageText = Files.readString(VILLAGE_FILE, StandardCharsets.UTF_8);
            JsonObject vc = GSON.fromJson(villageText, JsonObject.class);
            Map<String, List<String>> vcMap = new HashMap<>();
            JsonObject village = vc != null ? vc.getAsJsonObject("village_categories") : null;
            if (village != null) {
                for (String villageType : village.keySet()) {
                    List<String> cats = new ArrayList<>();
                    village.getAsJsonArray(villageType).forEach(e -> cats.add(e.getAsString()));
                    vcMap.put(villageType, cats);
                }
            }
            villageCategories = vcMap;
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to load foreign trade category config", e);
            categoryBase = new HashMap<>();
            villageCategories = new HashMap<>();
        }
    }

    public static Double getBasePrice(String category) {
        return category != null ? categoryBase.get(category) : null;
    }

    public static List<String> getVillageCategories(String villageType) {
        List<String> list = villageCategories.get(villageType);
        return list != null ? List.copyOf(list) : Collections.emptyList();
    }

    public static Set<String> getAllVillageTypes() {
        return Set.copyOf(villageCategories.keySet());
    }
}