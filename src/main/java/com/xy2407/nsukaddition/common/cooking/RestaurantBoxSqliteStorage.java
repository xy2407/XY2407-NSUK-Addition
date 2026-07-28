package com.xy2407.nsukaddition.common.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** 餐厅控制箱数据的 SQLite 持久化存储，支持单条存删与全量加载。 */
@SuppressWarnings("null")
public final class RestaurantBoxSqliteStorage {

    private RestaurantBoxSqliteStorage() {}

    public static void saveBox(ServerLevel level, RestaurantBoxData data) {
        if (level == null || data == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
                try (var connection = db.openConnection();
                     var ps = connection.prepareStatement(
                             "INSERT INTO restaurant_boxes(box_pos_long, building_id, definition_id, selected_recipe_id, "
                                     + "running, status_key, status_text, progress_ticks, cooldown_ticks, work_state, selected_cook_items, updated_at) "
                                     + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                     + "ON CONFLICT(box_pos_long) DO UPDATE SET "
                                     + "building_id = excluded.building_id, definition_id = excluded.definition_id, "
                                     + "selected_recipe_id = excluded.selected_recipe_id, running = excluded.running, "
                                     + "status_key = excluded.status_key, status_text = excluded.status_text, "
                                     + "progress_ticks = excluded.progress_ticks, cooldown_ticks = excluded.cooldown_ticks, "
                                     + "work_state = excluded.work_state, selected_cook_items = excluded.selected_cook_items, "
                                     + "updated_at = excluded.updated_at"
                     )) {
                    ps.setLong(1, data.boxPos().asLong());
                    ps.setString(2, data.buildingId());
                    ps.setString(3, data.definitionId());
                    ps.setString(4, data.selectedRecipeId());
                    ps.setInt(5, data.running() ? 1 : 0);
                    ps.setString(6, data.statusKey());
                    ps.setString(7, data.statusText());
                    ps.setInt(8, data.progressTicks());
                    ps.setInt(9, data.cooldownTicks());
                    ps.setString(10, data.workState());
                    ps.setString(11, String.join(",", data.selectedCookItems()));
                    ps.setLong(12, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to save restaurant box data", e);
            }
        });
    }

    public static void deleteBox(ServerLevel level, long boxPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
                try (var connection = db.openConnection()) {
                    try (var ps = connection.prepareStatement("DELETE FROM restaurant_boxes WHERE box_pos_long = ?")) {
                        ps.setLong(1, boxPosLong);
                        ps.executeUpdate();
                    }
                    try (var ps = connection.prepareStatement("DELETE FROM restaurant_occupied_seats WHERE box_pos_long = ?")) {
                        ps.setLong(1, boxPosLong);
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to delete restaurant box data", e);
            }
        });
    }

    /** 记录座位占用到 SQLite。 */
    public static void occupySeat(ServerLevel level, long boxPosLong, long seatPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
                try (var connection = db.openConnection();
                     var ps = connection.prepareStatement(
                             "INSERT OR IGNORE INTO restaurant_occupied_seats(box_pos_long, seat_pos_long) VALUES(?, ?)")) {
                    ps.setLong(1, boxPosLong);
                    ps.setLong(2, seatPosLong);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to save occupied seat", e);
            }
        });
    }

    /** 从 SQLite 删除座位占用记录。 */
    public static void freeSeat(ServerLevel level, long boxPosLong, long seatPosLong) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
                try (var connection = db.openConnection();
                     var ps = connection.prepareStatement(
                             "DELETE FROM restaurant_occupied_seats WHERE box_pos_long = ? AND seat_pos_long = ?")) {
                    ps.setLong(1, boxPosLong);
                    ps.setLong(2, seatPosLong);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to free occupied seat", e);
            }
        });
    }

    /** 清空所有座位占用记录（服务器启动时调用，因为重启后所有就餐会话均失效）。 */
    public static void clearAllOccupiedSeats(ServerLevel level) {
        if (level == null) return;
        try {
            NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
            try (var connection = db.openConnection();
                 var ps = connection.prepareStatement("DELETE FROM restaurant_occupied_seats")) {
                ps.executeUpdate();
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to clear occupied seats", e);
        }
    }

    public static CompoundTag loadAll(ServerLevel level) {
        if (level == null) return null;
        try {
            NsukSqliteDatabase db = NsukSqliteDatabase.get(level.getServer());
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            try (var connection = db.openConnection();
                 var ps = connection.prepareStatement("SELECT * FROM restaurant_boxes ORDER BY box_pos_long");
                 var rs = ps.executeQuery()) {
                while (rs.next()) {
                    CompoundTag box = new CompoundTag();
                    box.putLong("BoxPos", rs.getLong("box_pos_long"));
                    box.putString("BuildingId", rs.getString("building_id"));
                    box.putString("DefinitionId", rs.getString("definition_id"));
                    box.putString("SelectedRecipeId", rs.getString("selected_recipe_id"));
                    box.putBoolean("Running", rs.getInt("running") != 0);
                    box.putString("StatusKey", rs.getString("status_key"));
                    box.putString("StatusText", rs.getString("status_text"));
                    box.putInt("ProgressTicks", rs.getInt("progress_ticks"));
                    box.putInt("CooldownTicks", rs.getInt("cooldown_ticks"));
                    box.putString("WorkState", rs.getString("work_state"));
                    ListTag cookList = new ListTag();
                    String cookStr = rs.getString("selected_cook_items");
                    if (cookStr != null && !cookStr.isEmpty()) {
                        for (String item : cookStr.split(",")) {
                            if (!item.isEmpty()) cookList.add(StringTag.valueOf(item));
                        }
                    }
                    box.put("SelectedCookItems", cookList);
                    list.add(box);
                }
            }
            tag.put("Boxes", list);
            return list.isEmpty() ? null : tag;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load restaurant box data", e);
        }
    }
}
