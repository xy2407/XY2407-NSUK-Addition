package com.xy2407.nsukaddition.common.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** NSuk 独立库的 baseline 建表。结构为"最新完整结构"，历史库缺列由 NsukMigrations 的检查式迁移补齐。 */
public final class NsukSqliteSchema {

    private NsukSqliteSchema() {
    }

    public static void createBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS colony("
                    + "colony_id TEXT PRIMARY KEY, parent_city TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', "
                    + "core_pos_long INTEGER NOT NULL, dimension_id TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS colony_chunk("
                    + "colony_id TEXT NOT NULL, dimension TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, "
                    + "PRIMARY KEY (colony_id, dimension, chunk_x, chunk_z))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS colony_citizen("
                    + "citizen_uuid TEXT PRIMARY KEY, colony_id TEXT NOT NULL, assigned_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS breeding_boxes("
                    + "box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', "
                    + "selected_recipe_id TEXT NOT NULL DEFAULT '', running INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', "
                    + "status_text TEXT NOT NULL DEFAULT '', progress_ticks INTEGER NOT NULL DEFAULT 0, cooldown_ticks INTEGER NOT NULL DEFAULT 0, "
                    + "work_state TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS auto_restock("
                    + "box_pos_long INTEGER PRIMARY KEY, updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS foreign_trade_boxes("
                    + "box_pos_long INTEGER PRIMARY KEY, running INTEGER NOT NULL DEFAULT 0, "
                    + "status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', "
                    + "selected_trade_id TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS village_diplomacy("
                    + "player_uuid TEXT, village_type TEXT, village_pos_x INTEGER, village_pos_z INTEGER, "
                    + "city_id TEXT, city_name TEXT, established_at INTEGER, "
                    + "PRIMARY KEY(player_uuid, village_pos_x, village_pos_z))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS village_trade_quota("
                    + "player_uuid TEXT, city_id TEXT, item_id TEXT, "
                    + "daily_bought INTEGER DEFAULT 0, daily_sold INTEGER DEFAULT 0, reset_day INTEGER DEFAULT 0, "
                    + "PRIMARY KEY(player_uuid, city_id, item_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS village_city_type("
                    + "city_id TEXT PRIMARY KEY, village_type TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS village_stock("
                    + "city_id TEXT NOT NULL, item_id TEXT NOT NULL, stock INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(city_id, item_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS village_items("
                    + "city_id TEXT NOT NULL, item_id TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', "
                    + "PRIMARY KEY(city_id, item_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS foreign_trade_caravans("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "box_pos_long INTEGER NOT NULL,"
                    + "caravan_index INTEGER NOT NULL,"
                    + "name TEXT DEFAULT '',"
                    + "leader_uuid TEXT,"
                    + "status TEXT DEFAULT 'idle',"
                    + "target_city_id TEXT,"
                    + "return_day INTEGER DEFAULT 0,"
                    + "departure_day INTEGER DEFAULT 0,"
                    + "source_city_id TEXT DEFAULT '',"
                    + "spawn_city_id TEXT DEFAULT '',"
                    + "city_level INTEGER DEFAULT 0,"
                    + "dimension TEXT DEFAULT '',"
                    + "funds REAL DEFAULT 0,"
                    + "UNIQUE(box_pos_long, caravan_index))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS foreign_trade_caravan_products("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "box_pos_long INTEGER NOT NULL,"
                    + "caravan_index INTEGER NOT NULL,"
                    + "item_id TEXT NOT NULL,"
                    + "category TEXT DEFAULT '',"
                    + "`limit` INTEGER DEFAULT 0,"
                    + "stock INTEGER DEFAULT 0,"
                    + "UNIQUE(box_pos_long, caravan_index, item_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS foreign_trade_caravan_members("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "box_pos_long INTEGER NOT NULL,"
                    + "caravan_index INTEGER NOT NULL,"
                    + "citizen_uuid TEXT NOT NULL,"
                    + "citizen_name TEXT DEFAULT '',"
                    + "role TEXT DEFAULT '',"
                    + "UNIQUE(box_pos_long, citizen_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS foreign_trade_shopping_list("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "box_pos_long INTEGER NOT NULL,"
                    + "caravan_index INTEGER NOT NULL,"
                    + "city_id TEXT NOT NULL,"
                    + "item_id TEXT NOT NULL,"
                    + "quantity INTEGER NOT NULL,"
                    + "UNIQUE(box_pos_long, caravan_index, city_id, item_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS free_market_listings("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "city_id TEXT NOT NULL, "
                    + "city_name TEXT NOT NULL, "
                    + "item_id TEXT NOT NULL, "
                    + "count INTEGER NOT NULL, "
                    + "price INTEGER NOT NULL, "
                    + "seller_player TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "item_nbt TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS town_immigration("
                    + "request_id TEXT PRIMARY KEY, "
                    + "city_id TEXT NOT NULL, "
                    + "citizen_id TEXT NOT NULL, "
                    + "name TEXT NOT NULL DEFAULT '', "
                    + "grant_funds REAL NOT NULL DEFAULT 0, "
                    + "created_day INTEGER NOT NULL DEFAULT 0, "
                    + "spawn_pos_x INTEGER NOT NULL DEFAULT 0, "
                    + "spawn_pos_y INTEGER NOT NULL DEFAULT 0, "
                    + "spawn_pos_z INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS building_task_queue("
                    + "task_id TEXT PRIMARY KEY, "
                    + "citizen_id TEXT NOT NULL, "
                    + "city_id TEXT, "
                    + "dimension_id TEXT NOT NULL, "
                    + "build_box_x INTEGER NOT NULL, build_box_y INTEGER NOT NULL, build_box_z INTEGER NOT NULL, "
                    + "category TEXT NOT NULL, "
                    + "building_file_name TEXT NOT NULL, "
                    + "display_name TEXT NOT NULL, "
                    + "amount TEXT NOT NULL DEFAULT '', "
                    + "structure_file_name TEXT NOT NULL, "
                    + "origin_x INTEGER NOT NULL, origin_y INTEGER NOT NULL, origin_z INTEGER NOT NULL, "
                    + "rotation_degrees INTEGER NOT NULL, "
                    + "current_block_index INTEGER NOT NULL, "
                    + "total_blocks INTEGER NOT NULL, "
                    + "status TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL, "
                    + "replace_with_air INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS building_task_queue_pois("
                    + "task_id TEXT NOT NULL, "
                    + "poi_key TEXT NOT NULL, "
                    + "poi_type TEXT NOT NULL, "
                    + "capacity INTEGER NOT NULL, "
                    + "PRIMARY KEY(task_id, poi_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS daily_markers("
                    + "city_id TEXT NOT NULL, "
                    + "kind TEXT NOT NULL, "
                    + "day INTEGER NOT NULL, "
                    + "PRIMARY KEY(city_id, kind))");
        }
    }
}
