package com.xy2407.nsukaddition.common.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** 可捕获生物注册表：启动时读取游戏目录 xy2407_nsuk_addition/capturable_entities.json 中的实体注册 id，
 *  捕获器只允许捕获文件中定义的生物；文件不存在时生成默认原版生物列表(1.21.1)。 */
public final class CapturableEntityRegistry {

    private static final String EXTERNAL_DIR = "xy2407_nsuk_addition";
    private static final String FILE_NAME = "capturable_entities.json";

    private static final String[] DEFAULT_ENTITIES = {
            "minecraft:allay", "minecraft:armadillo", "minecraft:axolotl", "minecraft:bat",
            "minecraft:bee", "minecraft:camel", "minecraft:cat", "minecraft:chicken",
            "minecraft:cow", "minecraft:dolphin", "minecraft:donkey", "minecraft:fox",
            "minecraft:frog", "minecraft:goat", "minecraft:hoglin", "minecraft:horse",
            "minecraft:iron_golem", "minecraft:llama", "minecraft:mooshroom", "minecraft:mule",
            "minecraft:ocelot", "minecraft:panda", "minecraft:parrot", "minecraft:phantom",
            "minecraft:pig", "minecraft:polar_bear", "minecraft:rabbit", "minecraft:sheep",
            "minecraft:shulker", "minecraft:slime", "minecraft:sniffer", "minecraft:snow_golem",
            "minecraft:strider", "minecraft:tadpole", "minecraft:trader_llama", "minecraft:turtle",
            "minecraft:wolf",
            "meadow:water_buffalo", "meadow:wooly_cow",
            "wildernature:elephant", "wildernature:boar", "wildernature:squirrel",
            "wildernature:raccoon", "wildernature:swift_fox", "wildernature:minisheep",
            "wildernature:deer", "wildernature:lion", "wildernature:bison",
            "wildernature:giraffe", "wildernature:turkey", "wildernature:scorpion",
            "wildernature:hedgehog", "wildernature:hippo", "wildernature:beaver",
            "wildernature:dog", "wildernature:cassowary",
            "mysticsbiomes:strawberry_cow", "mysticsbiomes:vanilla_cow",
            "mysticsbiomes:chocolate_cow", "mysticsbiomes:rainbow_chicken",
            "mysticsbiomes:red_panda", "mysticsbiomes:sea_otter",
            "mysticsbiomes:caterpillar", "mysticsbiomes:butterfly"
    };

    private static volatile Set<String> capturableIds = Set.of();
    private static volatile boolean loaded = false;

    private CapturableEntityRegistry() {
    }

    public static void load() {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(FILE_NAME);
            if (!Files.exists(file)) {
                writeDefault(file);
            }
            Set<String> ids = new LinkedHashSet<>();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root != null && root.has("entities")) {
                    JsonArray array = root.getAsJsonArray("entities");
                    for (var element : array) {
                        String id = element.isJsonNull() ? null : element.getAsString();
                        if (id != null && !id.isBlank()) {
                            ids.add(id.trim());
                        }
                    }
                }
            }
            capturableIds = Set.copyOf(ids);
            loaded = true;
            NsukAddition.LOGGER.info("可捕获生物列表已加载: {} 种 ({}))", capturableIds.size(), file);
        } catch (Exception e) {
            NsukAddition.LOGGER.error("加载可捕获生物列表失败,回退默认原版列表", e);
            capturableIds = Set.of(DEFAULT_ENTITIES);
            loaded = true;
        }
    }

    public static boolean isCapturable(LivingEntity entity) {
        return entity != null && isCapturable(entity.getType());
    }

    public static boolean isCapturable(EntityType<?> type) {
        if (type == null || !loaded) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && capturableIds.contains(id.toString());
    }

    public static boolean isLoaded() {
        return loaded;
    }

    private static void writeDefault(Path file) throws Exception {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (String id : DEFAULT_ENTITIES) {
            array.add(id);
        }
        root.add("entities", array);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
    }
}