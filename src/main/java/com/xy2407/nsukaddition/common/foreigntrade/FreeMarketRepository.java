package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** 自由市场上架商品的数据访问层，负责free_market_listings表的CRUD。写入走官方 StorageWriteQueue 通道。 */
@SuppressWarnings("null")
public final class FreeMarketRepository {

    private static final AtomicReference<List<FreeMarketListing>> ALL_CACHE = new AtomicReference<>();
    private static volatile boolean refreshing = false;

    private FreeMarketRepository() {}

    public record FreeMarketListing(long id, String cityId, String cityName, String itemId, int count, int price, String sellerPlayer, long createdAt, String itemNbt) {}

    public static void ensureTable(NsukSqliteDatabase db) {
        if (db == null) return;
        try (var conn = db.openConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS free_market_listings("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "city_id TEXT NOT NULL, "
                    + "city_name TEXT NOT NULL, "
                    + "item_id TEXT NOT NULL, "
                    + "count INTEGER NOT NULL, "
                    + "price INTEGER NOT NULL, "
                    + "seller_player TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "item_nbt TEXT)");
            try {
                stmt.executeUpdate("ALTER TABLE free_market_listings ADD COLUMN item_nbt TEXT");
            } catch (SQLException ignored) {
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create free_market_listings table", e);
        }
    }

    public static void preloadAll() {
        triggerAsyncRefresh();
    }

    private static void triggerAsyncRefresh() {
        if (refreshing) return;
        refreshing = true;
        try {
            ALL_CACHE.set(queryAll());
        } catch (RuntimeException e) {
            NsukAddition.LOGGER.warn("Failed to refresh free market listings", e);
        } finally {
            refreshing = false;
        }
    }

    private static List<FreeMarketListing> queryAll() {
        List<FreeMarketListing> result = new ArrayList<>();
        var db = NsukSqliteDatabase.getInstance();
        if (db == null) return result;
        try (var conn = db.openConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT id, city_id, city_name, item_id, count, price, seller_player, created_at, item_nbt "
                             + "FROM free_market_listings ORDER BY created_at")) {
            while (rs.next()) {
                result.add(readRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query all free market listings", e);
        }
        return List.copyOf(result);
    }

    private static void invalidateCache() {
        ALL_CACHE.set(null);
    }

    public static void clearCache() {
        ALL_CACHE.set(null);
        refreshing = false;
    }

    public static void insert(String cityId, String cityName, String itemId, int count, int price, String sellerPlayer, String itemNbt) {
        NsukSqliteDatabase db = NsukSqliteDatabase.getInstance();
        if (db == null) return;
        WriteBatchBuffer.submit(db, "free_market_listings",
                "nsuk_free_market:insert:" + cityId + ":" + itemId + ":" + sellerPlayer, connection -> {
            try (var ps = connection.prepareStatement(
                    "INSERT INTO free_market_listings(city_id, city_name, item_id, count, price, seller_player, created_at, item_nbt) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cityId);
                ps.setString(2, cityName);
                ps.setString(3, itemId);
                ps.setInt(4, count);
                ps.setInt(5, price);
                ps.setString(6, sellerPlayer);
                ps.setLong(7, System.currentTimeMillis());
                ps.setString(8, itemNbt != null ? itemNbt : "");
                ps.executeUpdate();
            }
            invalidateCache();
        });
    }

    public static void delete(long id) {
        NsukSqliteDatabase db = NsukSqliteDatabase.getInstance();
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "free_market_listings", "nsuk_free_market:id:" + id, connection -> {
            try (var ps = connection.prepareStatement("DELETE FROM free_market_listings WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            invalidateCache();
        });
    }

    public static void updatePriceAndCount(long id, int newCount, int newPrice) {
        NsukSqliteDatabase db = NsukSqliteDatabase.getInstance();
        if (db == null) return;
        WriteBatchBuffer.submit(db, "free_market_listings", "nsuk_free_market:id:" + id, connection -> {
            try (var ps = connection.prepareStatement(
                    "UPDATE free_market_listings SET count = ?, price = ? WHERE id = ?")) {
                ps.setInt(1, newCount);
                ps.setInt(2, newPrice);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            invalidateCache();
        });
    }

    public static List<FreeMarketListing> getByCity(String cityId) {
        List<FreeMarketListing> all = ALL_CACHE.get();
        if (all != null) {
            List<FreeMarketListing> result = new ArrayList<>();
            for (FreeMarketListing l : all) {
                if (l.cityId().equals(cityId)) {
                    result.add(l);
                }
            }
            return result;
        }
        return queryByCity(cityId);
    }

    public static List<FreeMarketListing> getOtherCities(String excludeCityId) {
        List<FreeMarketListing> all = ALL_CACHE.get();
        if (all != null) {
            List<FreeMarketListing> result = new ArrayList<>();
            for (FreeMarketListing l : all) {
                if (!l.cityId().equals(excludeCityId)) {
                    result.add(l);
                }
            }
            return result;
        }
        return queryOtherCities(excludeCityId);
    }

    public static FreeMarketListing getById(long id) {
        List<FreeMarketListing> all = ALL_CACHE.get();
        if (all != null) {
            for (FreeMarketListing l : all) {
                if (l.id() == id) return l;
            }
            return null;
        }
        return queryById(id);
    }

    private static List<FreeMarketListing> queryByCity(String cityId) {
        List<FreeMarketListing> result = new ArrayList<>();
        var db = NsukSqliteDatabase.getInstance();
        if (db == null) return result;
        try (var conn = db.openConnection();
             var ps = conn.prepareStatement(
                     "SELECT id, city_id, city_name, item_id, count, price, seller_player, created_at, item_nbt "
                             + "FROM free_market_listings WHERE city_id = ? ORDER BY created_at")) {
            ps.setString(1, cityId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query free market listings by city", e);
        }
        return result;
    }

    private static List<FreeMarketListing> queryOtherCities(String excludeCityId) {
        List<FreeMarketListing> result = new ArrayList<>();
        var db = NsukSqliteDatabase.getInstance();
        if (db == null) return result;
        try (var conn = db.openConnection();
             var ps = conn.prepareStatement(
                     "SELECT id, city_id, city_name, item_id, count, price, seller_player, created_at, item_nbt "
                             + "FROM free_market_listings WHERE city_id != ? ORDER BY created_at")) {
            ps.setString(1, excludeCityId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query free market listings from other cities", e);
        }
        return result;
    }

    private static FreeMarketListing queryById(long id) {
        var db = NsukSqliteDatabase.getInstance();
        if (db == null) return null;
        try (var conn = db.openConnection();
             var ps = conn.prepareStatement(
                     "SELECT id, city_id, city_name, item_id, count, price, seller_player, created_at, item_nbt "
                             + "FROM free_market_listings WHERE id = ?")) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return readRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query free market listing by id", e);
        }
        return null;
    }

    private static FreeMarketListing readRow(ResultSet rs) throws SQLException {
        return new FreeMarketListing(
                rs.getLong("id"),
                rs.getString("city_id"),
                rs.getString("city_name"),
                rs.getString("item_id"),
                rs.getInt("count"),
                rs.getInt("price"),
                rs.getString("seller_player"),
                rs.getLong("created_at"),
                rs.getString("item_nbt") != null ? rs.getString("item_nbt") : ""
        );
    }
}