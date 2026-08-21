package com.xy2407.nsukaddition.common.foreigntrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 村庄交易配置，从JSON加载按村庄类型分组的交易物品定义。 */
public final class VillageTradeConfig {

    private static final Path CONFIG_DIR = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("village_trades.json");
    private static final String RESOURCE_PATH = "/data/xy2407_nsuk_addition/trade/village_trades.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record VillageTradeDef(String item_id, int count, double buy, double sell, int daily_buy_limit, int daily_sell_limit) {
        public VillageTradeDef(String item_id, int count, double buy, double sell, int daily_buy_limit, int daily_sell_limit) {
            this.item_id = item_id;
            this.count = count;
            this.buy = buy;
            this.sell = sell;
            this.daily_buy_limit = daily_buy_limit;
            this.daily_sell_limit = daily_sell_limit;
        }
    }

    public record VillageTradesConfig(Map<String, List<VillageTradeDef>> village_trades) {
        public VillageTradesConfig(Map<String, List<VillageTradeDef>> village_trades) {
            this.village_trades = village_trades != null ? village_trades : Collections.emptyMap();
        }
    }

    private static Map<String, List<VillageTradeDef>> entries = Collections.emptyMap();

    private VillageTradeConfig() {}

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            syncFromResource();
            load();
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to init village trade config", e);
            entries = Collections.emptyMap();
        }
    }

    private static void syncFromResource() {
        try {
            byte[] resourceBytes = null;
            try (var is = VillageTradeConfig.class.getResourceAsStream(RESOURCE_PATH)) {
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
                NsukAddition.LOGGER.info("Synced village trade config from resources to {}", CONFIG_FILE);
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to sync village trade config", e);
        }
    }

    private static void load() {
        try {
            String text = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            VillageTradesConfig config = GSON.fromJson(text, VillageTradesConfig.class);
            entries = config != null && config.village_trades() != null ? config.village_trades() : Collections.emptyMap();
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to load village trade config", e);
            entries = Collections.emptyMap();
        }
    }

    public static List<VillageTradeDef> getTrades(String villageType) {
        List<VillageTradeDef> list = entries.get(villageType);
        return list != null ? List.copyOf(list) : new ArrayList<>();
    }

    public static java.util.Set<String> getAllVillageTypes() {
        return java.util.Set.copyOf(entries.keySet());
    }
}