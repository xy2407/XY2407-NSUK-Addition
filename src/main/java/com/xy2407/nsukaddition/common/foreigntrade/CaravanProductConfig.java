package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 商队产品配置：按来源村庄城市等级决定商队携带的商品种类与单商品库存上限。
 * - 材料类(基准分类)：聚落 5 种(必含木头/石头/砖石)…都市全部材料
 * - 作物/动物/酒水/矿产/奶酪按等级递增
 * - 动物/奶酪分类当前外贸配置未收录物品时对应产品为空(等配置补齐)
 * - 初始库存 = 单商品上限 × 50%
 * - 水产一律不考虑
 */
public final class CaravanProductConfig {

    public static final double INIT_STOCK_RATIO = 0.50D;

    private static final String[] MANDATORY_MATERIAL_CATS = {"wood", "stone", "brick"};

    private CaravanProductConfig() {
    }

    public record CaravanProduct(String itemId, String category, int limit) {
        public int initStock() {
            return (int) Math.round(limit * INIT_STOCK_RATIO);
        }
    }

    public static List<CaravanProduct> pickProducts(CityLevel level, RandomSource rng) {
        if (level == null) {
            level = CityLevel.VILLAGE;
        }
        List<CaravanProduct> result = new ArrayList<>();
        switch (level) {
            case SETTLEMENT -> {
                pickMaterial(result, rng, 5, 500, true);
            }
            case VILLAGE -> {
                pickMaterial(result, rng, 7, 1000, false);
                pickCategory(result, rng, "crop", 4, 500);
                pickCategory(result, rng, "animal", 4, 16);
                pickMineral(result, rng, 3, 300, 0, 0, false);
            }
            case TOWN -> {
                pickMaterial(result, rng, 9, 1500, false);
                pickCategory(result, rng, "crop", 6, 800);
                pickCategory(result, rng, "animal", 6, 24);
                pickMineral(result, rng, 0, 500, 64, 32, true);
                pickCategory(result, rng, "cheese", 4, 32);
                pickCategory(result, rng, "wine", 12, 64);
            }
            case CITY_STATE -> {
                pickMaterial(result, rng, 11, 2000, false);
                pickCategory(result, rng, "crop", 8, 1200);
                pickCategory(result, rng, "animal", 8, 32);
                pickMineral(result, rng, 0, 1200, 128, 64, true);
                pickCategory(result, rng, "cheese", 5, 64);
                pickCategory(result, rng, "wine", 16, 128);
            }
            case METROPOLIS -> {
                pickAllMaterial(result, 3000);
                pickAllCategory(result, "crop", 2000);
                pickAllCategory(result, "animal", 64);
                pickAllCategory(result, "wine", 192);
                pickMineral(result, null, 0, 1000, 256, 128, true);
                pickAllCategory(result, "cheese", 128);
            }
        }
        return result;
    }

    private static void pickMaterial(List<CaravanProduct> out, RandomSource rng, int n, int limit, boolean mandatory) {
        List<TradeItemDef> pool = materialPool();
        if (pool.isEmpty()) {
            return;
        }
        List<TradeItemDef> candidates = new ArrayList<>(pool);
        if (mandatory) {
            for (String cat : MANDATORY_MATERIAL_CATS) {
                List<TradeItemDef> catPool = candidates.stream().filter(d -> cat.equalsIgnoreCase(d.category())).toList();
                if (!catPool.isEmpty()) {
                    out.add(new CaravanProduct(catPool.get(rng.nextInt(catPool.size())).item_id(), cat, limit));
                }
            }
            candidates.removeIf(d -> out.stream().anyMatch(p -> p.itemId().equals(d.item_id())));
        }
        shuffle(candidates, rng);
        int remaining = n - out.size();
        for (int i = 0; i < remaining && i < candidates.size(); i++) {
            TradeItemDef d = candidates.get(i);
            out.add(new CaravanProduct(d.item_id(), d.category(), limit));
        }
    }

    private static void pickAllMaterial(List<CaravanProduct> out, int limit) {
        for (TradeItemDef d : materialPool()) {
            out.add(new CaravanProduct(d.item_id(), d.category(), limit));
        }
    }

    private static void pickCategory(List<CaravanProduct> out, RandomSource rng, String category, int n, int limit) {
        List<TradeItemDef> pool = categoryPool(category);
        if (pool.isEmpty()) {
            return;
        }
        List<TradeItemDef> shuffled = new ArrayList<>(pool);
        shuffle(shuffled, rng);
        for (int i = 0; i < n && i < shuffled.size(); i++) {
            TradeItemDef d = shuffled.get(i);
            out.add(new CaravanProduct(d.tradeKey(), d.category(), limit));
        }
    }

    private static void pickAllCategory(List<CaravanProduct> out, String category, int limit) {
        for (TradeItemDef d : categoryPool(category)) {
            out.add(new CaravanProduct(d.tradeKey(), d.category(), limit));
        }
    }

    private static void pickMineral(List<CaravanProduct> out, RandomSource rng, int n,
                                    int normalLimit, int diamondLimit, int netheriteLimit, boolean includePrecious) {
        List<TradeItemDef> pool = categoryPool("mineral");
        if (pool.isEmpty()) {
            return;
        }
        List<TradeItemDef> candidates = new ArrayList<>();
        for (TradeItemDef d : pool) {
            boolean precious = isPrecious(d.item_id());
            if (!includePrecious && precious) {
                continue;
            }
            candidates.add(d);
        }
        List<TradeItemDef> selected;
        if (n > 0) {
            List<TradeItemDef> shuffled = new ArrayList<>(candidates);
            shuffle(shuffled, rng);
            selected = shuffled.subList(0, Math.min(n, shuffled.size()));
        } else {
            selected = candidates;
        }
        for (TradeItemDef d : selected) {
            String id = d.item_id().toLowerCase(Locale.ROOT);
            int itemLimit = normalLimit;
            if (id.contains("netherite")) {
                itemLimit = netheriteLimit;
            } else if (id.contains("diamond")) {
                itemLimit = diamondLimit;
            }
            out.add(new CaravanProduct(d.item_id(), d.category(), itemLimit));
        }
    }

    private static <T> void shuffle(List<T> list, RandomSource rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private static List<TradeItemDef> materialPool() {
        List<TradeItemDef> result = new ArrayList<>();
        for (TradeItemDef d : ForeignTradeConfig.getEntries()) {
            if (VillageStockConfig.isMaterialCategory(d.category())) {
                result.add(d);
            }
        }
        return result;
    }

    private static List<TradeItemDef> categoryPool(String category) {
        List<TradeItemDef> result = new ArrayList<>();
        for (TradeItemDef d : ForeignTradeConfig.getEntries()) {
            if (category.equalsIgnoreCase(d.category())) {
                result.add(d);
            }
        }
        return result;
    }

    private static boolean isPrecious(String itemId) {
        if (itemId == null) {
            return false;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        return id.contains("diamond") || id.contains("netherite");
    }
}