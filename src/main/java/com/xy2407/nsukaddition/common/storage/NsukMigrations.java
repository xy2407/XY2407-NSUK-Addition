package com.xy2407.nsukaddition.common.storage;

import common.cn.kafei.simukraft.storage.core.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** NSuk 独立库的迁移清单。baseline 为最新结构，历史库缺列由检查式迁移补齐，新旧库均幂等。 */
public final class NsukMigrations {

    private NsukMigrations() {
    }

    public static List<Migration> all() {
        return List.of(new AddLegacyColumns(), new RebuildVillageTradeQuota(), new EnsureRestaurantTables());
    }

    private static final class AddLegacyColumns implements Migration {
        @Override
        public int version() {
            return 2;
        }

        @Override
        public String description() {
            return "backfill legacy NSuk columns";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            addColumnIfMissing(connection, "restaurant_boxes", "selected_cook_items", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "restaurant_boxes", "maid_waiter_ids", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "restaurant_boxes", "maid_waiter_names", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "village_diplomacy", "city_name", "TEXT");
            addColumnIfMissing(connection, "foreign_trade_caravans", "source_city_id", "TEXT DEFAULT ''");
            addColumnIfMissing(connection, "foreign_trade_caravans", "spawn_city_id", "TEXT DEFAULT ''");
            addColumnIfMissing(connection, "foreign_trade_caravans", "city_level", "INTEGER DEFAULT 0");
            addColumnIfMissing(connection, "foreign_trade_caravans", "dimension", "TEXT DEFAULT ''");
            addColumnIfMissing(connection, "foreign_trade_caravans", "funds", "REAL DEFAULT 0");
            addColumnIfMissing(connection, "foreign_trade_caravan_members", "role", "TEXT DEFAULT ''");
            addColumnIfMissing(connection, "free_market_listings", "item_nbt", "TEXT");
        }
    }

    private static final class RebuildVillageTradeQuota implements Migration {
        @Override
        public int version() {
            return 3;
        }

        @Override
        public String description() {
            return "rebuild village_trade_quota with city_id";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            if (hasColumn(connection, "village_trade_quota", "village_type")) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE village_trade_quota RENAME TO village_trade_quota_old");
                    statement.executeUpdate("CREATE TABLE village_trade_quota("
                            + "player_uuid TEXT, city_id TEXT, item_id TEXT, "
                            + "daily_bought INTEGER DEFAULT 0, daily_sold INTEGER DEFAULT 0, reset_day INTEGER DEFAULT 0, "
                            + "PRIMARY KEY(player_uuid, city_id, item_id))");
                    statement.executeUpdate("DROP TABLE village_trade_quota_old");
                }
            }
        }
    }

    private static final class EnsureRestaurantTables implements Migration {
        @Override
        public int version() {
            return 4;
        }

        @Override
        public String description() {
            return "ensure restaurant tables exist";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS restaurant_boxes("
                        + "box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', "
                        + "selected_recipe_id TEXT NOT NULL DEFAULT '', running INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', "
                        + "status_text TEXT NOT NULL DEFAULT '', progress_ticks INTEGER NOT NULL DEFAULT 0, cooldown_ticks INTEGER NOT NULL DEFAULT 0, "
                        + "work_state TEXT NOT NULL DEFAULT '', selected_cook_items TEXT NOT NULL DEFAULT '', "
                        + "maid_waiter_ids TEXT NOT NULL DEFAULT '', maid_waiter_names TEXT NOT NULL DEFAULT '', "
                        + "updated_at INTEGER NOT NULL DEFAULT 0)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS restaurant_occupied_seats("
                        + "box_pos_long INTEGER NOT NULL, seat_pos_long INTEGER NOT NULL, "
                        + "PRIMARY KEY (box_pos_long, seat_pos_long))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS restaurant_orders("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "box_pos_long INTEGER NOT NULL, "
                        + "customer_id TEXT NOT NULL, "
                        + "seat_pos_long INTEGER NOT NULL, "
                        + "recipe_id TEXT NOT NULL DEFAULT '', "
                        + "status TEXT NOT NULL DEFAULT 'PENDING')");
            }
        }
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        if (!hasColumn(connection, table, column)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (column.equals(rs.getString("name"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
