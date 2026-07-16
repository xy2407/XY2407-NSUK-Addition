package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** 自由市场上架商品的数据访问层，负责free_market_listings表的CRUD。 */
@SuppressWarnings("null")
public final class FreeMarketRepository {

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

    public static long insert(String cityId, String cityName, String itemId, int count, int price, String sellerPlayer, String itemNbt) {
        AtomicLong resultId = new AtomicLong(-1);
        NsukWriteExecutor.submitSync(() -> {
            var db = NsukSqliteDatabase.getInstance();
            if (db == null) return;
            try (var conn = db.openConnection();
                 var ps = conn.prepareStatement(
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
                try (var rs = ps.getGeneratedKeys()) {
                    if (rs.next()) resultId.set(rs.getLong(1));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert free market listing", e);
            }
        });
        return resultId.get();
    }

    public static void delete(long id) {
        NsukWriteExecutor.submit(() -> {
            var db = NsukSqliteDatabase.getInstance();
            if (db == null) return;
            try (var conn = db.openConnection();
                 var ps = conn.prepareStatement("DELETE FROM free_market_listings WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete free market listing", e);
            }
        });
    }

    public static void updatePriceAndCount(long id, int newCount, int newPrice) {
        NsukWriteExecutor.submit(() -> {
            var db = NsukSqliteDatabase.getInstance();
            if (db == null) return;
            try (var conn = db.openConnection();
                 var ps = conn.prepareStatement(
                         "UPDATE free_market_listings SET count = ?, price = ? WHERE id = ?")) {
                ps.setInt(1, newCount);
                ps.setInt(2, newPrice);
                ps.setLong(3, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update free market listing", e);
            }
        });
    }

    public static List<FreeMarketListing> getByCity(String cityId) {
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

    public static List<FreeMarketListing> getOtherCities(String excludeCityId) {
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

    public static FreeMarketListing getById(long id) {
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
