package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外贸市场:按游戏天确定性浮动价格,基准分类按分类基准价值派生,村庄按分类映射随机抽取可售物品。
 */
public final class ForeignTradeMarket {

    private static final double FLUCTUATION_MIN = -0.15;
    private static final double FLUCTUATION_MAX = 0.25;
    private static final double SELL_RATIO = 0.8;
    private static final int VILLAGE_ITEMS_PER_CATEGORY = 6;

    private static int lastRefreshDay = -1;
    private static final ConcurrentHashMap<String, MarketEntry> currentPrices = new ConcurrentHashMap<>();

    public record MarketEntry(String itemId, int count, double buyPrice, double sellPrice, String category, String villageType) {}

    private ForeignTradeMarket() {}

    public static void ensureRefreshed() {
        int day = currentGameDay();
        if (day != lastRefreshDay || currentPrices.isEmpty()) {
            refresh(day);
        }
    }

    public static void refresh() {
        refresh(currentGameDay());
    }

    private static void refresh(int day) {
        currentPrices.clear();

        Map<String, List<TradeItemDef>> byCategory = new HashMap<>();
        for (var def : ForeignTradeConfig.getEntries()) {
            byCategory.computeIfAbsent(def.category(), k -> new ArrayList<>()).add(def);
        }

        for (var def : ForeignTradeConfig.getEntries()) {
            Double base = ForeignTradeCategoryConfig.getBasePrice(def.category());
            double buyPrice;
            double sellPrice;
            if (base != null) {
                buyPrice = round1(base * (1.0 + flucFor(day, def.item_id())));
                sellPrice = round1(buyPrice * SELL_RATIO);
            } else {
                buyPrice = round1(def.buy());
                sellPrice = round1(Math.min(def.sell(), buyPrice * SELL_RATIO));
            }
            if (sellPrice > buyPrice) {
                sellPrice = buyPrice;
            }
            String key = def.tradeKey();
            currentPrices.put(key,
                    new MarketEntry(key, def.count(), buyPrice, sellPrice, def.category(), ""));
        }

        for (String villageType : ForeignTradeCategoryConfig.getAllVillageTypes()) {
            Random rng = new Random(day * 104729L + villageType.hashCode());
            for (String category : ForeignTradeCategoryConfig.getVillageCategories(villageType)) {
                List<TradeItemDef> pool = byCategory.getOrDefault(category, List.of());
                if (pool.isEmpty()) {
                    continue;
                }
                List<TradeItemDef> shuffled = new ArrayList<>(pool);
                Collections.shuffle(shuffled, rng);
                int n = Math.min(VILLAGE_ITEMS_PER_CATEGORY, shuffled.size());
                for (int i = 0; i < n; i++) {
                    TradeItemDef def = shuffled.get(i);
                    String key = def.tradeKey();
                    MarketEntry global = currentPrices.get(key);
                    if (global == null) {
                        continue;
                    }
                    currentPrices.put(villageType + ":" + key,
                            new MarketEntry(key, def.count(), global.buyPrice(), global.sellPrice(), "village", villageType));
                }
            }
        }
        lastRefreshDay = day;
    }

    private static double flucFor(int day, String itemId) {
        double r = new Random(day * 7919L + itemId.hashCode()).nextDouble();
        return FLUCTUATION_MIN + r * (FLUCTUATION_MAX - FLUCTUATION_MIN);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static int currentGameDay() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? (int) (server.overworld().getDayTime() / 24000L) : 0;
    }

    public static List<MarketEntry> getMarketEntries() {
        ensureRefreshed();
        return new ArrayList<>(currentPrices.values());
    }

    public static List<MarketEntry> getMarketEntriesForPlayer(ServerLevel level, UUID playerUuid) {
        ensureRefreshed();
        if (level == null || playerUuid == null) return List.of();
        var relations = DiplomacyStorage.loadRelations(level, playerUuid);
        if (relations.isEmpty()) return List.of();
        Set<String> villageTypes = new HashSet<>();
        for (var r : relations) {
            if (r.villageType() != null && !r.villageType().isEmpty()) {
                villageTypes.add(r.villageType());
            }
        }
        if (villageTypes.isEmpty()) return List.of();
        List<MarketEntry> result = new ArrayList<>();
        for (var entry : currentPrices.values()) {
            if (entry.villageType() != null && !entry.villageType().isEmpty()
                    && villageTypes.contains(entry.villageType())) {
                result.add(entry);
            }
        }
        return result;
    }

    public static MarketEntry getEntry(String itemId) {
        ensureRefreshed();
        return currentPrices.get(itemId);
    }

    public static MarketEntry getEntry(String villageType, String itemId) {
        ensureRefreshed();
        if (villageType == null || villageType.isEmpty()) {
            return currentPrices.get(itemId);
        }
        return currentPrices.get(villageType + ":" + itemId);
    }
}