package com.xy2407.nsukaddition.common.cooking;

import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 餐厅控制箱数据的 SQLite 持久化存储，支持单条存删与全量加载。写入走 WriteBatchBuffer 批量缓冲，删除/释放座位立即落库。 */
@SuppressWarnings("null")
public final class RestaurantBoxSqliteStorage {

    private static final String TABLE = "restaurant_boxes";

    private RestaurantBoxSqliteStorage() {}

    public static void saveBox(ServerLevel level, RestaurantBoxData data, Runnable onComplete) {
        if (level == null || data == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        CompoundTag snapshot = data.toTag();
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        long boxPosLong = snapshot.getLong("BoxPos");
        WriteBatchBuffer.submit(db, TABLE, "nsuk_restaurant_box:" + boxPosLong, connection -> {
            try (var ps = connection.prepareStatement(
                    "INSERT INTO restaurant_boxes(box_pos_long, building_id, definition_id, selected_recipe_id, "
                            + "running, status_key, status_text, progress_ticks, cooldown_ticks, work_state, selected_cook_items, "
                            + "maid_waiter_ids, maid_waiter_names, updated_at) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(box_pos_long) DO UPDATE SET "
                            + "building_id = excluded.building_id, definition_id = excluded.definition_id, "
                            + "selected_recipe_id = excluded.selected_recipe_id, running = excluded.running, "
                            + "status_key = excluded.status_key, status_text = excluded.status_text, "
                            + "progress_ticks = excluded.progress_ticks, cooldown_ticks = excluded.cooldown_ticks, "
                            + "work_state = excluded.work_state, selected_cook_items = excluded.selected_cook_items, "
                            + "maid_waiter_ids = excluded.maid_waiter_ids, maid_waiter_names = excluded.maid_waiter_names, "
                            + "updated_at = excluded.updated_at"
            )) {
                ps.setLong(1, boxPosLong);
                ps.setString(2, snapshot.getString("BuildingId"));
                ps.setString(3, snapshot.getString("DefinitionId"));
                ps.setString(4, snapshot.getString("SelectedRecipeId"));
                ps.setInt(5, snapshot.getBoolean("Running") ? 1 : 0);
                ps.setString(6, snapshot.getString("StatusKey"));
                ps.setString(7, snapshot.getString("StatusText"));
                ps.setInt(8, snapshot.getInt("ProgressTicks"));
                ps.setInt(9, snapshot.getInt("CooldownTicks"));
                ps.setString(10, snapshot.getString("WorkState"));
                ListTag cookList = snapshot.getList("SelectedCookItems", net.minecraft.nbt.Tag.TAG_STRING);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cookList.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(cookList.getString(i));
                }
                ps.setString(11, sb.toString());
                ListTag maidList = snapshot.getList("MaidWaiters", net.minecraft.nbt.Tag.TAG_COMPOUND);
                StringBuilder idSb = new StringBuilder();
                StringBuilder nameSb = new StringBuilder();
                for (int i = 0; i < maidList.size(); i++) {
                    if (i > 0) { idSb.append(','); nameSb.append(','); }
                    idSb.append(maidList.getCompound(i).getUUID("MaidId"));
                    nameSb.append(maidList.getCompound(i).getString("MaidName"));
                }
                ps.setString(12, idSb.toString());
                ps.setString(13, nameSb.toString());
                ps.setLong(14, System.currentTimeMillis());
                ps.executeUpdate();
            }
            try (var psDel = connection.prepareStatement(
                    "DELETE FROM restaurant_orders WHERE box_pos_long = ?")) {
                psDel.setLong(1, boxPosLong);
                psDel.executeUpdate();
            }
            ListTag ordersList = snapshot.getList("Orders", CompoundTag.TAG_COMPOUND);
            if (!ordersList.isEmpty()) {
                try (var psIns = connection.prepareStatement(
                        "INSERT INTO restaurant_orders(box_pos_long, customer_id, seat_pos_long, recipe_id, status) "
                                + "VALUES(?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < ordersList.size(); i++) {
                        CompoundTag ot = ordersList.getCompound(i);
                        UUID customerId = ot.getUUID("Customer");
                        if (customerId == null) continue;
                        psIns.setLong(1, boxPosLong);
                        psIns.setString(2, customerId.toString());
                        psIns.setLong(3, ot.getLong("Seat"));
                        psIns.setString(4, ot.getString("Recipe"));
                        String status = ot.getString("Status");
                        psIns.setString(5, status.isEmpty() ? "PENDING" : status);
                        psIns.addBatch();
                    }
                    psIns.executeBatch();
                }
            }
        });
        if (onComplete != null) onComplete.run();
    }

    public static void deleteBox(ServerLevel level, long boxPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) return;
        WriteBatchBuffer.submitPriority(db, TABLE, "nsuk_restaurant_box:" + boxPosLong, connection -> {
            try (var ps = connection.prepareStatement("DELETE FROM restaurant_boxes WHERE box_pos_long = ?")) {
                ps.setLong(1, boxPosLong);
                ps.executeUpdate();
            }
            try (var ps = connection.prepareStatement("DELETE FROM restaurant_occupied_seats WHERE box_pos_long = ?")) {
                ps.setLong(1, boxPosLong);
                ps.executeUpdate();
            }
            try (var ps = connection.prepareStatement("DELETE FROM restaurant_orders WHERE box_pos_long = ?")) {
                ps.setLong(1, boxPosLong);
                ps.executeUpdate();
            }
        });
    }

    public static void occupySeat(ServerLevel level, long boxPosLong, long seatPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) return;
        String key = "nsuk_restaurant_seat:" + boxPosLong + ":" + seatPosLong;
        WriteBatchBuffer.submit(db, "restaurant_occupied_seats", key, connection -> {
            try (var ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO restaurant_occupied_seats(box_pos_long, seat_pos_long) VALUES(?, ?)")) {
                ps.setLong(1, boxPosLong);
                ps.setLong(2, seatPosLong);
                ps.executeUpdate();
            }
        });
    }

    public static void freeSeat(ServerLevel level, long boxPosLong, long seatPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) return;
        String key = "nsuk_restaurant_seat:" + boxPosLong + ":" + seatPosLong;
        WriteBatchBuffer.submitPriority(db, "restaurant_occupied_seats", key, connection -> {
            try (var ps = connection.prepareStatement(
                    "DELETE FROM restaurant_occupied_seats WHERE box_pos_long = ? AND seat_pos_long = ?")) {
                ps.setLong(1, boxPosLong);
                ps.setLong(2, seatPosLong);
                ps.executeUpdate();
            }
        });
    }

    public static void clearAllOccupiedSeats(ServerLevel level) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) return;
        db.callSync(connection -> {
            try (var ps = connection.prepareStatement("DELETE FROM restaurant_occupied_seats")) {
                ps.executeUpdate();
            }
            return null;
        });
    }

    public static CompoundTag loadAll(ServerLevel level) {
        if (level == null) return null;
        try {
            NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            try (var connection = db.openConnection()) {
                ensureTables(connection);
                Map<Long, ListTag> ordersByBox = new HashMap<>();
                try (var ops = connection.prepareStatement("SELECT * FROM restaurant_orders ORDER BY box_pos_long");
                     var ors = ops.executeQuery()) {
                    while (ors.next()) {
                        CompoundTag ot = new CompoundTag();
                        String customerIdStr = ors.getString("customer_id");
                        if (customerIdStr == null || customerIdStr.isEmpty()) continue;
                        try {
                            ot.putUUID("Customer", UUID.fromString(customerIdStr));
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        ot.putLong("Seat", ors.getLong("seat_pos_long"));
                        ot.putString("Recipe", ors.getString("recipe_id"));
                        String status = ors.getString("status");
                        ot.putString("Status", status == null || status.isEmpty() ? "PENDING" : status);
                        ordersByBox.computeIfAbsent(ors.getLong("box_pos_long"), k -> new ListTag()).add(ot);
                    }
                }
                try (var ps = connection.prepareStatement("SELECT * FROM restaurant_boxes ORDER BY box_pos_long");
                     var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long boxPosLong = rs.getLong("box_pos_long");
                        CompoundTag box = new CompoundTag();
                        box.putLong("BoxPos", boxPosLong);
                        box.putString("BuildingId", rs.getString("building_id"));
                        box.putString("DefinitionId", rs.getString("definition_id"));
                        box.putString("SelectedRecipeId", rs.getString("selected_recipe_id"));
                        box.putBoolean("Running", rs.getInt("running") != 0);
                        box.putString("StatusKey", rs.getString("status_key"));
                        box.putString("StatusText", rs.getString("status_text"));
                        box.putInt("ProgressTicks", rs.getInt("progress_ticks"));
                        box.putInt("CooldownTicks", rs.getInt("cooldown_ticks"));
                        box.putString("WorkState", rs.getString("work_state"));
                        box.put("Orders", ordersByBox.getOrDefault(boxPosLong, new ListTag()));
                        ListTag cookList = new ListTag();
                        String cookStr = rs.getString("selected_cook_items");
                        if (cookStr != null && !cookStr.isEmpty()) {
                            for (String item : cookStr.split(",")) {
                                if (!item.isEmpty()) cookList.add(StringTag.valueOf(item));
                            }
                        }
                        box.put("SelectedCookItems", cookList);
                        ListTag maidList = new ListTag();
                        String idStr = rs.getString("maid_waiter_ids");
                        String nameStr = rs.getString("maid_waiter_names");
                        if (idStr != null && !idStr.isEmpty()) {
                            String[] ids = idStr.split(",");
                            String[] names = (nameStr == null || nameStr.isEmpty()) ? new String[0] : nameStr.split(",");
                            for (int i = 0; i < ids.length; i++) {
                                if (ids[i].isEmpty()) continue;
                                try {
                                    CompoundTag mt = new CompoundTag();
                                    mt.putUUID("MaidId", UUID.fromString(ids[i]));
                                    mt.putString("MaidName", i < names.length ? names[i] : "");
                                    maidList.add(mt);
                                } catch (IllegalArgumentException e) {
                                }
                            }
                        }
                        box.put("MaidWaiters", maidList);
                        list.add(box);
                    }
                }
            }
            tag.put("Boxes", list);
            return list.isEmpty() ? null : tag;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load restaurant box data", e);
        }
    }

    private static void ensureTables(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
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