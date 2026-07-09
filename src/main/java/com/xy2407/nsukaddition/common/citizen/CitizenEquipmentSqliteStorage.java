package com.xy2407.nsukaddition.common.citizen;

import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 市民盔甲装备数据的 SQLite 持久化存储，用于跨实体重生/传送时恢复装备。 */
@SuppressWarnings("null")
public final class CitizenEquipmentSqliteStorage {

    private static final ConcurrentMap<String, SimuSqliteDatabase> DATABASES = new ConcurrentHashMap<>();

    private CitizenEquipmentSqliteStorage() {}

    private static SimuSqliteDatabase openDatabase(MinecraftServer server) {
        String key = SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent(key, ignored -> {
            SimuSqliteDatabase db = SimuSqliteDatabase.open(server);
            initTable(db);
            return db;
        });
    }

    private static void initTable(SimuSqliteDatabase database) {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS citizen_equipment("
                            + "uuid TEXT PRIMARY KEY, "
                            + "head BLOB, "
                            + "chest BLOB, "
                            + "legs BLOB, "
                            + "feet BLOB"
                            + ")"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize citizen_equipment table in SQLite", exception);
        }
    }

    public static void clearServerCache(MinecraftServer server) {
        if (server == null) return;
        DATABASES.remove(SimuSqliteDatabase.databasePath(server).toAbsolutePath().normalize().toString());
    }

    public static void save(ServerLevel level, UUID citizenUuid, Map<EquipmentSlot, ItemStack> equipment) {
        if (level == null || citizenUuid == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO citizen_equipment(uuid, head, chest, legs, feet) VALUES(?, ?, ?, ?, ?) "
                                 + "ON CONFLICT(uuid) DO UPDATE SET head = excluded.head, chest = excluded.chest, "
                                 + "legs = excluded.legs, feet = excluded.feet"
                 )) {
                ps.setString(1, citizenUuid.toString());
                ps.setBytes(2, stackToBytes(level, equipment.get(EquipmentSlot.HEAD)));
                ps.setBytes(3, stackToBytes(level, equipment.get(EquipmentSlot.CHEST)));
                ps.setBytes(4, stackToBytes(level, equipment.get(EquipmentSlot.LEGS)));
                ps.setBytes(5, stackToBytes(level, equipment.get(EquipmentSlot.FEET)));
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    public static Map<EquipmentSlot, ItemStack> load(ServerLevel level, UUID citizenUuid) {
        Map<EquipmentSlot, ItemStack> result = new EnumMap<>(EquipmentSlot.class);
        if (level == null || citizenUuid == null) return result;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "SELECT head, chest, legs, feet FROM citizen_equipment WHERE uuid = ?")) {
                ps.setString(1, citizenUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put(EquipmentSlot.HEAD, bytesToStack(level, rs.getBytes("head")));
                        result.put(EquipmentSlot.CHEST, bytesToStack(level, rs.getBytes("chest")));
                        result.put(EquipmentSlot.LEGS, bytesToStack(level, rs.getBytes("legs")));
                        result.put(EquipmentSlot.FEET, bytesToStack(level, rs.getBytes("feet")));
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static void delete(ServerLevel level, UUID citizenUuid) {
        if (level == null || citizenUuid == null) return;
        try {
            SimuSqliteDatabase db = openDatabase(level.getServer());
            try (Connection connection = db.openConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM citizen_equipment WHERE uuid = ?")) {
                ps.setString(1, citizenUuid.toString());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    private static byte[] stackToBytes(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        net.minecraft.nbt.Tag tag = stack.save(level.registryAccess());
        return tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static ItemStack bytesToStack(ServerLevel level, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return ItemStack.EMPTY;
        try {
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(json);
            return ItemStack.parse(level.registryAccess(), tag).orElse(ItemStack.EMPTY);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
