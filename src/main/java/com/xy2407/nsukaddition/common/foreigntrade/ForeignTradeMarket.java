package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外贸市场:每 10 分钟确定性浮动价格,每个村庄每件物品的购买与出售各自独立波动 -15% ~ +25%,
 * 出售价不得超过收购价;基准分类按分类基准价值派生。
 */
public final class ForeignTradeMarket {

    private static final double FLUCTUATION_MIN = -0.15;
    private static final double FLUCTUATION_MAX = 0.25;
    private static final long PRICE_SLOT_TICKS = 12000L;

    private static int lastRefreshSlot = -1;
    private static final ConcurrentHashMap<String, MarketEntry> currentPrices = new ConcurrentHashMap<>();

    public record MarketEntry(String itemId, int count, double buyPrice, double sellPrice, String category, String villageType) {}

    private ForeignTradeMarket() {}

    public static void ensureRefreshed() {
        int slot = currentPriceSlot();
        if (slot != lastRefreshSlot || currentPrices.isEmpty()) {
            refresh(slot);
        }
    }

    public static void refresh() {
        refresh(currentPriceSlot());
    }

    private static void refresh(int slot) {
        currentPrices.clear();

        for (var def : ForeignTradeConfig.getEntries()) {
            double[] p = priceFor(slot, def.tradeKey(), def);
            currentPrices.put(def.tradeKey(),
                    new MarketEntry(def.tradeKey(), def.count(), p[0], p[1], def.category(), ""));
        }

        for (String villageType : ForeignTradeCategoryConfig.getAllVillageTypes()) {
            Set<String> enabled = new HashSet<>(ForeignTradeCategoryConfig.getVillageCategories(villageType));
            for (TradeItemDef def : ForeignTradeConfig.getEntries()) {
                if (!enabled.contains(def.category())) {
                    continue;
                }
                String villageKey = villageType + ":" + def.tradeKey();
                double[] p = priceFor(slot, villageKey, def);
                currentPrices.put(villageKey,
                        new MarketEntry(def.tradeKey(), def.count(), p[0], p[1], def.category(), villageType));
            }
        }
        lastRefreshSlot = slot;
    }

    /** 每个村庄每件物品的购买与出售各自独立波动，出售价不超过收购价（超过则直接相等）。 */
    private static double[] priceFor(int slot, String key, TradeItemDef def) {
        Double base = ForeignTradeCategoryConfig.getBasePrice(def.category());
        double buyBase = base != null ? base : def.buy();
        double sellBase = base != null ? base : def.sell();
        double buyPrice = round2(buyBase * (1.0 + flucFor(slot, key, 0)));
        double sellPrice = round2(sellBase * (1.0 + flucFor(slot, key, 1)));
        if (sellPrice > buyPrice) {
            sellPrice = buyPrice;
        }
        return new double[]{buyPrice, sellPrice};
    }

    private static double flucFor(int slot, String key, int salt) {
        double r = new Random((long) slot * 7919L + key.hashCode() * 31L + salt).nextDouble();
        return FLUCTUATION_MIN + r * (FLUCTUATION_MAX - FLUCTUATION_MIN);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int currentPriceSlot() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? (int) (server.overworld().getDayTime() / PRICE_SLOT_TICKS) : 0;
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