package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 村庄库存系统：
 * - 库存按村庄实例(cityId，城市 UUID)存储，多玩家与同一村庄外贸共用同一份库存。
 * - 村庄经营商品集合在首次初始化时从村庄分类中随机抽取并持久化(village_items)，之后固定。
 * - 初始库存：材料类(基准分类)1000~1500 随机，其余分类 = 上限×60%。
 * - 每日补货：库存 ≤ 上限60% 时补上限20%(封顶60%)；库存 &gt; 上限60% 时清除超出部分且不补货
 *   (为玩家出售预留空间)。
 * - 出售受村庄单商品总库存限制(≤ 上限)，购买需库存 &gt; 0。
 */
@SuppressWarnings("null")
public final class VillageStockService {

    private static final int ITEMS_PER_CATEGORY = 6;

    private static final ConcurrentHashMap<String, Integer> STOCK_CACHE = new ConcurrentHashMap<>();

    private VillageStockService() {
    }

    private static void ensureTable(ServerLevel level) {
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS village_stock("
                             + "city_id TEXT NOT NULL, item_id TEXT NOT NULL, stock INTEGER NOT NULL DEFAULT 0,"
                             + "PRIMARY KEY(city_id, item_id))")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create village_stock table", e);
        }
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS village_items("
                             + "city_id TEXT NOT NULL, item_id TEXT NOT NULL, category TEXT NOT NULL DEFAULT '',"
                             + "PRIMARY KEY(city_id, item_id))")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create village_items table", e);
        }
    }

    public static void ensureVillage(ServerLevel level, UUID cityId, String villageType) {
        if (level == null || cityId == null || villageType == null) {
            return;
        }
        ensureTable(level);
        String cityKey = cityId.toString();
        if (hasItems(level, cityKey)) {
            return;
        }
        List<StockItem> items = pickVillageItems(level, cityId, villageType);
        if (items.isEmpty()) {
            return;
        }
        Random rng = new Random(cityId.hashCode() * 31L + villageType.hashCode());
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority(db, "village_stock", "village:init:" + cityKey, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO village_items(city_id, item_id, category) VALUES(?, ?, ?)")) {
                for (StockItem si : items) {
                    ps.setString(1, cityKey);
                    ps.setString(2, si.itemId);
                    ps.setString(3, si.category);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO village_stock(city_id, item_id, stock) VALUES(?, ?, ?)")) {
                for (StockItem si : items) {
                    int limit = VillageStockConfig.getCategoryLimit(si.category, si.itemId);
                    if (limit <= 0) {
                        continue;
                    }
                    int init;
                    if (VillageStockConfig.isMaterialCategory(si.category)) {
                        init = VillageStockConfig.MATERIAL_INIT_MIN
                                + rng.nextInt(VillageStockConfig.MATERIAL_INIT_MAX - VillageStockConfig.MATERIAL_INIT_MIN + 1);
                    } else {
                        init = (int) Math.round(limit * VillageStockConfig.STOCK_CAP_RATIO);
                    }
                    ps.setString(1, cityKey);
                    ps.setString(2, si.itemId);
                    ps.setInt(3, init);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private static List<StockItem> pickVillageItems(ServerLevel level, UUID cityId, String villageType) {
        Map<String, List<TradeItemDef>> byCategory = new HashMap<>();
        for (TradeItemDef def : ForeignTradeConfig.getEntries()) {
            byCategory.computeIfAbsent(def.category(), k -> new ArrayList<>()).add(def);
        }
        Random rng = new Random(cityId.hashCode() * 31L + villageType.hashCode());
        List<StockItem> result = new ArrayList<>();
        for (String category : ForeignTradeCategoryConfig.getVillageCategories(villageType)) {
            List<TradeItemDef> pool = byCategory.getOrDefault(category, List.of());
            if (pool.isEmpty()) {
                continue;
            }
            List<TradeItemDef> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, rng);
            int n = Math.min(ITEMS_PER_CATEGORY, shuffled.size());
            for (int i = 0; i < n; i++) {
                TradeItemDef def = shuffled.get(i);
                result.add(new StockItem(def.tradeKey(), def.category()));
            }
        }
        return result;
    }

    private static boolean hasItems(ServerLevel level, String cityKey) {
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return true;
        }
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM village_items WHERE city_id = ? LIMIT 1")) {
            ps.setString(1, cityKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check village items", e);
        }
    }

    public static int getStock(ServerLevel level, UUID cityId, String itemId) {
        if (level == null || cityId == null || itemId == null) {
            return 0;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return 0;
        }
        String cacheKey = cityId + ":" + itemId;
        Integer cached = STOCK_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT stock FROM village_stock WHERE city_id = ? AND item_id = ?")) {
            ps.setString(1, cityId.toString());
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                int stock = rs.next() ? rs.getInt("stock") : 0;
                STOCK_CACHE.put(cacheKey, stock);
                return stock;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load village stock", e);
        }
    }

    public static boolean canSell(ServerLevel level, UUID cityId, String itemId, String category) {
        if (!isVillageItem(level, cityId, itemId)) {
            return false;
        }
        int limit = VillageStockConfig.getCategoryLimit(category, itemId);
        if (limit <= 0) {
            return false;
        }
        return getStock(level, cityId, itemId) < limit;
    }

    public static boolean canBuy(ServerLevel level, UUID cityId, String itemId) {
        if (!isVillageItem(level, cityId, itemId)) {
            return false;
        }
        return getStock(level, cityId, itemId) > 0;
    }

    public static boolean isVillageItem(ServerLevel level, UUID cityId, String itemId) {
        if (level == null || cityId == null || itemId == null) {
            return false;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return false;
        }
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM village_items WHERE city_id = ? AND item_id = ?")) {
            ps.setString(1, cityId.toString());
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check village item", e);
        }
    }

    public static void addStock(ServerLevel level, UUID cityId, String itemId, String category, int amount) {
        if (level == null || cityId == null || itemId == null || amount <= 0) {
            return;
        }
        int limit = VillageStockConfig.getCategoryLimit(category, itemId);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority(db, "village_stock",
                "village:add:" + cityId + ":" + itemId + ":" + System.nanoTime(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO village_stock(city_id, item_id, stock) VALUES(?, ?, ?) "
                            + "ON CONFLICT(city_id, item_id) DO UPDATE SET stock = MIN(stock + ?, ?)")) {
                ps.setString(1, cityId.toString());
                ps.setString(2, itemId);
                ps.setInt(3, amount);
                ps.setInt(4, amount);
                ps.setInt(5, limit);
                ps.executeUpdate();
            }
        });
        STOCK_CACHE.remove(cityId + ":" + itemId);
    }

    public static void removeStock(ServerLevel level, UUID cityId, String itemId, int amount) {
        if (level == null || cityId == null || itemId == null || amount <= 0) {
            return;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority(db, "village_stock",
                "village:remove:" + cityId + ":" + itemId + ":" + System.nanoTime(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE village_stock SET stock = MAX(stock - ?, 0) WHERE city_id = ? AND item_id = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, cityId.toString());
                ps.setString(3, itemId);
                ps.executeUpdate();
            }
        });
        STOCK_CACHE.remove(cityId + ":" + itemId);
    }

    public static void tickDailyRestock(ServerLevel level) {
        if (level == null) {
            return;
        }
        ensureTable(level);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        List<StockRow> rows = new ArrayList<>();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT v.city_id, v.item_id, v.stock, i.category FROM village_stock v "
                             + "LEFT JOIN village_items i ON v.city_id = i.city_id AND v.item_id = i.item_id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new StockRow(rs.getString("city_id"), rs.getString("item_id"),
                            rs.getInt("stock"), rs.getString("category")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all village stock", e);
        }
        if (rows.isEmpty()) {
            return;
        }
        NsukSqliteDatabase dbw = NsukSqliteDatabase.get(level.getServer());
        if (dbw == null) {
            return;
        }
        WriteBatchBuffer.submit(dbw, "village_stock", "village:restock", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE village_stock SET stock = ? WHERE city_id = ? AND item_id = ?")) {
                for (StockRow row : rows) {
                    int limit = VillageStockConfig.getCategoryLimit(row.category, row.itemId);
                    if (limit <= 0) {
                        continue;
                    }
                    int cap = (int) Math.round(limit * VillageStockConfig.STOCK_CAP_RATIO);
                    int newStock;
                    if (row.stock > cap) {
                        newStock = cap;
                    } else {
                        int restock = (int) Math.round(limit * VillageStockConfig.RESTOCK_RATIO);
                        newStock = Math.min(row.stock + restock, cap);
                    }
                    if (newStock == row.stock) {
                        continue;
                    }
                    ps.setInt(1, newStock);
                    ps.setString(2, row.cityId);
                    ps.setString(3, row.itemId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        STOCK_CACHE.clear();
    }

    private record StockItem(String itemId, String category) {
    }

    private record StockRow(String cityId, String itemId, int stock, String category) {
    }
}