package com.xy2407.nsukaddition.common.foreigntrade;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** 外贸市场，管理价格浮动和当前市场报价。 */
public final class ForeignTradeMarket {

    private static final double FLUCTUATION_MIN = 0.0;
    private static final double FLUCTUATION_MAX = 0.25;

    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private static final Random RNG = new Random();
    private static long lastRefreshTime = 0;
    private static final ConcurrentHashMap<String, MarketEntry> currentPrices = new ConcurrentHashMap<>();

    public record MarketEntry(String itemId, int count, int buyPrice, int sellPrice, String category) {}

    private ForeignTradeMarket() {}

    public static void ensureRefreshed() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > REFRESH_INTERVAL_MS || currentPrices.isEmpty()) {
            refresh();
        }
    }

    public static void refresh() {
        currentPrices.clear();
        for (var def : ForeignTradeConfig.getEntries()) {
            double buyFluc = FLUCTUATION_MIN + RNG.nextDouble() * (FLUCTUATION_MAX - FLUCTUATION_MIN);
            double sellFluc = FLUCTUATION_MIN + RNG.nextDouble() * (FLUCTUATION_MAX - FLUCTUATION_MIN);
            int buyPrice = Math.max(1, (int) Math.round(def.buy() * (1.0 + buyFluc)));
            int sellPrice = Math.max(1, (int) Math.round(def.sell() * (1.0 + sellFluc)));
            currentPrices.put(def.item_id(), new MarketEntry(def.item_id(), def.count(), buyPrice, sellPrice, def.category()));
        }
        lastRefreshTime = System.currentTimeMillis();
    }

    public static List<MarketEntry> getMarketEntries() {
        ensureRefreshed();
        return new ArrayList<>(currentPrices.values());
    }

    public static MarketEntry getEntry(String itemId) {
        ensureRefreshed();
        return currentPrices.get(itemId);
    }
}
