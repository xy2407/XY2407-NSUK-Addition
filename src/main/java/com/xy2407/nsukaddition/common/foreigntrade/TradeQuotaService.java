package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 交易配额服务，按城市ID管理每日交易配额，带内存缓存与按游戏日重置。 */
@SuppressWarnings("null")
public final class TradeQuotaService {

    private static final int DEFAULT_DAILY_BUY_LIMIT = 64;
    private static final int DEFAULT_DAILY_SELL_LIMIT = 48;

    private static final ConcurrentHashMap<String, QuotaData> CACHE = new ConcurrentHashMap<>();

    public record QuotaData(int dailyBought, int dailySold, int resetDay) {}

    private TradeQuotaService() {}

    private static String cacheKey(UUID playerUuid, String cityId, String itemId) {
        return playerUuid.toString() + ":" + cityId + ":" + itemId;
    }

    private static int currentDay(ServerLevel level) {
        return (int) (level.getDayTime() / 24000L);
    }

    public static void resetQuotasIfNeeded(ServerLevel level, UUID playerUuid) {
        if (level == null || playerUuid == null) return;
        int day = currentDay(level);
        String prefix = playerUuid.toString() + ":";
        for (var entry : CACHE.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            QuotaData data = entry.getValue();
            if (data.resetDay() < day) {
                entry.setValue(new QuotaData(0, 0, day));
            }
        }
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "village_trade_quota",
                "quota:reset:" + playerUuid + ":" + day, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE village_trade_quota SET daily_bought = 0, daily_sold = 0, reset_day = ? " +
                            "WHERE player_uuid = ? AND reset_day < ?")) {
                ps.setInt(1, day);
                ps.setString(2, playerUuid.toString());
                ps.setInt(3, day);
                ps.executeUpdate();
            }
        });
    }

    public static int getRemainingBuyQuota(ServerLevel level, UUID playerUuid, String cityId, String itemId) {
        if (level == null || playerUuid == null || cityId == null || itemId == null) return 0;
        resetQuotasIfNeeded(level, playerUuid);
        VillageTradeConfig.VillageTradeDef def = findTradeDefByCityId(level, cityId, itemId);
        int limit = def != null ? def.daily_buy_limit() : DEFAULT_DAILY_BUY_LIMIT;
        QuotaData data = getQuotaData(level, playerUuid, cityId, itemId);
        return Math.max(0, limit - data.dailyBought());
    }

    public static int getRemainingSellQuota(ServerLevel level, UUID playerUuid, String cityId, String itemId) {
        if (level == null || playerUuid == null || cityId == null || itemId == null) return 0;
        resetQuotasIfNeeded(level, playerUuid);
        VillageTradeConfig.VillageTradeDef def = findTradeDefByCityId(level, cityId, itemId);
        int limit = def != null ? def.daily_sell_limit() : DEFAULT_DAILY_SELL_LIMIT;
        QuotaData data = getQuotaData(level, playerUuid, cityId, itemId);
        return Math.max(0, limit - data.dailySold());
    }

    public static void recordBuy(ServerLevel level, UUID playerUuid, String cityId, String itemId, int amount) {
        if (level == null || playerUuid == null || cityId == null || itemId == null || amount <= 0) return;
        resetQuotasIfNeeded(level, playerUuid);
        String key = cacheKey(playerUuid, cityId, itemId);
        QuotaData data = getQuotaData(level, playerUuid, cityId, itemId);
        QuotaData updated = new QuotaData(data.dailyBought() + amount, data.dailySold(), data.resetDay());
        CACHE.put(key, updated);
        int day = currentDay(level);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "village_trade_quota",
                "quota:buy:" + key + ":" + System.nanoTime(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO village_trade_quota(player_uuid, city_id, item_id, daily_bought, daily_sold, reset_day) " +
                            "VALUES(?, ?, ?, ?, 0, ?) " +
                            "ON CONFLICT(player_uuid, city_id, item_id) DO UPDATE SET " +
                            "daily_bought = CASE WHEN village_trade_quota.reset_day < ? THEN ? ELSE village_trade_quota.daily_bought + ? END, " +
                            "daily_sold = CASE WHEN village_trade_quota.reset_day < ? THEN 0 ELSE village_trade_quota.daily_sold END, " +
                            "reset_day = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, cityId);
                ps.setString(3, itemId);
                ps.setInt(4, amount);
                ps.setInt(5, day);
                ps.setInt(6, day);
                ps.setInt(7, amount);
                ps.setInt(8, amount);
                ps.setInt(9, day);
                ps.setInt(10, day);
                ps.executeUpdate();
            }
        });
    }

    public static void recordSell(ServerLevel level, UUID playerUuid, String cityId, String itemId, int amount) {
        if (level == null || playerUuid == null || cityId == null || itemId == null || amount <= 0) return;
        resetQuotasIfNeeded(level, playerUuid);
        String key = cacheKey(playerUuid, cityId, itemId);
        QuotaData data = getQuotaData(level, playerUuid, cityId, itemId);
        QuotaData updated = new QuotaData(data.dailyBought(), data.dailySold() + amount, data.resetDay());
        CACHE.put(key, updated);
        int day = currentDay(level);
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, "village_trade_quota",
                "quota:sell:" + key + ":" + System.nanoTime(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO village_trade_quota(player_uuid, city_id, item_id, daily_bought, daily_sold, reset_day) " +
                            "VALUES(?, ?, ?, 0, ?, ?) " +
                            "ON CONFLICT(player_uuid, city_id, item_id) DO UPDATE SET " +
                            "daily_sold = CASE WHEN village_trade_quota.reset_day < ? THEN ? ELSE village_trade_quota.daily_sold + ? END, " +
                            "daily_bought = CASE WHEN village_trade_quota.reset_day < ? THEN 0 ELSE village_trade_quota.daily_bought END, " +
                            "reset_day = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, cityId);
                ps.setString(3, itemId);
                ps.setInt(4, amount);
                ps.setInt(5, day);
                ps.setInt(6, day);
                ps.setInt(7, amount);
                ps.setInt(8, amount);
                ps.setInt(9, day);
                ps.setInt(10, day);
                ps.executeUpdate();
            }
        });
    }

    private static QuotaData getQuotaData(ServerLevel level, UUID playerUuid, String cityId, String itemId) {
        String key = cacheKey(playerUuid, cityId, itemId);
        QuotaData cached = CACHE.get(key);
        if (cached != null) return cached;
        QuotaData data = queryQuota(level, playerUuid, cityId, itemId);
        QuotaData existing = CACHE.putIfAbsent(key, data);
        return existing != null ? existing : data;
    }

    private static QuotaData queryQuota(ServerLevel level, UUID playerUuid, String cityId, String itemId) {
        NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
        int day = currentDay(level);
        if (db == null) return new QuotaData(0, 0, day);
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT daily_bought, daily_sold, reset_day FROM village_trade_quota " +
                             "WHERE player_uuid = ? AND city_id = ? AND item_id = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, cityId);
            ps.setString(3, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int resetDay = rs.getInt("reset_day");
                    if (resetDay < day) {
                        return new QuotaData(0, 0, day);
                    }
                    return new QuotaData(rs.getInt("daily_bought"), rs.getInt("daily_sold"), resetDay);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query trade quota", e);
        }
        return new QuotaData(0, 0, day);
    }

    private static VillageTradeConfig.VillageTradeDef findTradeDefByCityId(ServerLevel level, String cityId, String itemId) {
        UUID cityUuid;
        try {
            cityUuid = UUID.fromString(cityId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String villageType = VillageCityTypeStorage.getVillageType(level, cityUuid);
        if (villageType == null) return null;
        for (VillageTradeConfig.VillageTradeDef def : VillageTradeConfig.getTrades(villageType)) {
            if (def.item_id().equals(itemId)) return def;
        }
        return null;
    }
}