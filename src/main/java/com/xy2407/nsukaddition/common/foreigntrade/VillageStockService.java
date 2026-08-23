package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final ConcurrentHashMap<String, Integer> STOCK_CACHE = new ConcurrentHashMap<>();

    private static final java.util.Set<String> SYNCED_CITIES = ConcurrentHashMap.newKeySet();

    private VillageStockService() {
    }

    static CityLevel villageCityLevel(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return CityLevel.SETTLEMENT;
        }
        int chunks = level == null ? 0 : CityChunkManager.get(level).getCityChunks(cityId).size();
        return switch (VillageCityGrade.gradeForChunks(chunks)) {
            case VillageCityGrade.HAMLET -> CityLevel.SETTLEMENT;
            case VillageCityGrade.VILLAGE -> CityLevel.VILLAGE;
            case VillageCityGrade.TOWN -> CityLevel.TOWN;
            default -> CityLevel.CITY_STATE;
        };
    }

    public static int villageCap(ServerLevel level, UUID cityId, String category) {
        return CaravanProductConfig.unitLimit(villageCityLevel(level, cityId), category) * 2;
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
        if (!SYNCED_CITIES.add(cityId.toString())) {
            return;
        }
        List<StockItem> items = pickVillageItems(level, cityId, villageType);
        if (items.isEmpty()) {
            return;
        }
        for (StockItem si : items) {
            if (villageCap(level, cityId, si.category) > 0) {
                ensureItemTradable(level, cityId, si.itemId, si.category);
            }
        }
    }

    private static List<StockItem> pickVillageItems(ServerLevel level, UUID cityId, String villageType) {
        Set<String> enabled = new HashSet<>(
                ForeignTradeCategoryConfig.getVillageCategories(villageType));
        List<StockItem> result = new ArrayList<>();
        for (TradeItemDef def : ForeignTradeConfig.getEntries()) {
            if (enabled.contains(def.category())) {
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
        String cacheKey = cityId + ":" + itemId;
        Integer cached = STOCK_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return 0;
        }
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT stock FROM village_stock WHERE city_id = ? AND item_id = ?")) {
            ps.setString(1, cityId.toString());
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int stock = rs.getInt("stock");
                    STOCK_CACHE.put(cacheKey, stock);
                    return stock;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load village stock", e);
        }
        return 0;
    }

    public static boolean canSell(ServerLevel level, UUID cityId, String itemId, String category) {
        if (!isVillageItem(level, cityId, itemId)) {
            return false;
        }
        int cap = villageCap(level, cityId, category);
        if (cap <= 0) {
            return false;
        }
        return getStock(level, cityId, itemId) < cap;
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

    public static void ensureItemTradable(ServerLevel level, UUID cityId, String itemId, String category) {
        if (level == null || cityId == null || itemId == null || category == null) {
            return;
        }
        if (isVillageItem(level, cityId, itemId)) {
            return;
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        int cap = villageCap(level, cityId, category);
        if (cap <= 0) {
            return;
        }
        int init = (int) Math.round(cap * 0.5D);
        String cityKey = cityId.toString();
        try (Connection conn = db.openConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO village_items(city_id, item_id, category) VALUES(?, ?, ?)")) {
                ps.setString(1, cityKey);
                ps.setString(2, itemId);
                ps.setString(3, category);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO village_stock(city_id, item_id, stock) VALUES(?, ?, ?)")) {
                ps.setString(1, cityKey);
                ps.setString(2, itemId);
                ps.setInt(3, init);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure village item stock", e);
        }
        STOCK_CACHE.put(cityId + ":" + itemId, init);
    }

    public static void addStock(ServerLevel level, UUID cityId, String itemId, String category, int amount) {
        if (level == null || cityId == null || itemId == null || amount <= 0) {
            return;
        }
        int cap = villageCap(level, cityId, category);
        String cacheKey = cityId + ":" + itemId;
        STOCK_CACHE.put(cacheKey, Math.min(cap, getStock(level, cityId, itemId) + amount));
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority(db, "village_stock",
                "village:add:" + cacheKey + ":" + System.nanoTime(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO village_stock(city_id, item_id, stock) VALUES(?, ?, ?) "
                            + "ON CONFLICT(city_id, item_id) DO UPDATE SET stock = MIN(stock + ?, ?)")) {
                ps.setString(1, cityId.toString());
                ps.setString(2, itemId);
                ps.setInt(3, amount);
                ps.setInt(4, amount);
                ps.setInt(5, cap);
                ps.executeUpdate();
            }
        });
    }

    public static void removeStock(ServerLevel level, UUID cityId, String itemId, int amount) {
        if (level == null || cityId == null || itemId == null || amount <= 0) {
            return;
        }
        String cacheKey = cityId + ":" + itemId;
        STOCK_CACHE.put(cacheKey, Math.max(0, getStock(level, cityId, itemId) - amount));
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority(db, "village_stock",
                "village:remove:" + cacheKey + ":" + System.nanoTime(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE village_stock SET stock = MAX(stock - ?, 0) WHERE city_id = ? AND item_id = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, cityId.toString());
                ps.setString(3, itemId);
                ps.executeUpdate();
            }
        });
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
                    UUID cityUuid;
                    try {
                        cityUuid = UUID.fromString(row.cityId);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    int cap = villageCap(level, cityUuid, row.category);
                    if (cap <= 0) {
                        continue;
                    }
                    int target = (int) Math.round(cap * 0.5D);
                    int step = Math.max(1, (int) Math.round(cap * VillageStockConfig.RESTOCK_RATIO));
                    int newStock;
                    if (row.stock > target) {
                        newStock = Math.max(target, row.stock - step);
                    } else if (row.stock < target) {
                        newStock = Math.min(target, row.stock + step);
                    } else {
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