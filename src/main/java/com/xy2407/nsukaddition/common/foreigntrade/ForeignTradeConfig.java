package com.xy2407.nsukaddition.common.foreigntrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 外贸交易配置，仅从JSON文件加载可交易物品定义。 */
public final class ForeignTradeConfig {

    private static final Path CONFIG_DIR = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("foreign_trade_items.json");
    private static final String RESOURCE_PATH = "/data/xy2407_nsuk_addition/trade/foreign_trade_items.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record TradeNbt(String entity, Boolean baby) {
    }

    public record TradeItemDef(String item_id, int count, double buy, double sell, String category, TradeNbt nbt) {
        public TradeItemDef(String item_id, int count, double buy, double sell, String category) {
            this(item_id, count, buy, sell, category, null);
        }

        public TradeItemDef(String item_id, int count, double buy, double sell, String category, TradeNbt nbt) {
            this.item_id = item_id;
            this.count = count;
            this.buy = buy;
            this.sell = sell;
            this.category = category != null ? category : "other";
            this.nbt = nbt;
        }

        public boolean isAnimal() {
            return category != null && category.equalsIgnoreCase("animal");
        }

        public String captureEntityId() {
            if (nbt != null && nbt.entity() != null && !nbt.entity().isBlank()) {
                return nbt.entity();
            }
            return item_id;
        }

        public boolean captureBaby() {
            return nbt != null && Boolean.TRUE.equals(nbt.baby());
        }

        public String tradeKey() {
            return isAnimal() ? captureEntityId() : item_id;
        }
    }

    public record TradeConfig(List<TradeItemDef> trades) {}

    private static List<TradeItemDef> entries = new ArrayList<>();

    private ForeignTradeConfig() {}

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            syncFromResource();
            load();
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to init foreign trade config", e);
            entries = new ArrayList<>();
        }
    }

    private static void syncFromResource() {
        try {
            byte[] resourceBytes = null;
            try (var is = ForeignTradeConfig.class.getResourceAsStream(RESOURCE_PATH)) {
                if (is != null) {
                    resourceBytes = is.readAllBytes();
                }
            }
            if (resourceBytes == null) {
                NsukAddition.LOGGER.warn("Resource {} not found, skipping sync", RESOURCE_PATH);
                return;
            }
            byte[] diskBytes = null;
            if (Files.exists(CONFIG_FILE)) {
                diskBytes = Files.readAllBytes(CONFIG_FILE);
            }
            if (diskBytes == null || !java.util.Arrays.equals(resourceBytes, diskBytes)) {
                Files.write(CONFIG_FILE, resourceBytes);
                NsukAddition.LOGGER.info("Synced foreign trade config from resources to {}", CONFIG_FILE);
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to sync foreign trade config", e);
        }
    }

    private static void load() {
        try {
            String text = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            TradeConfig config = GSON.fromJson(text, TradeConfig.class);
            entries = config != null && config.trades() != null ? config.trades() : new ArrayList<>();
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to load foreign trade config", e);
            entries = new ArrayList<>();
        }
    }

    public static List<TradeItemDef> getEntries() {
        return List.copyOf(entries);
    }

    public static TradeItemDef find(String key) {
        if (key == null) {
            return null;
        }
        for (TradeItemDef d : entries) {
            if (key.equals(d.tradeKey()) || key.equals(d.item_id())) {
                return d;
            }
        }
        return null;
    }
}