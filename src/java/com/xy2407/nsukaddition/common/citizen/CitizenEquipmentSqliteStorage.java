package com.xy2407.nsukaddition.common.citizen;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** 市民盔甲装备数据的 SQLite 持久化存储，写操作异步执行避免SQLITE_BUSY。 */
@SuppressWarnings("null")
public final class CitizenEquipmentSqliteStorage {

    private CitizenEquipmentSqliteStorage() {}

    private static NsukSqliteDatabase openDatabase(MinecraftServer server) {
        return NsukSqliteDatabase.get(server);
    }

    public static void clearServerCache(MinecraftServer server) {
        NsukSqliteDatabase.clearServerCache(server);
    }

    public static void save(ServerLevel level, UUID citizenUuid, Map<EquipmentSlot, ItemStack> equipment) {
        if (level == null || citizenUuid == null) return;
        MinecraftServer server = level.getServer();
        byte[] head = stackToBytes(level, equipment.get(EquipmentSlot.HEAD));
        byte[] chest = stackToBytes(level, equipment.get(EquipmentSlot.CHEST));
        byte[] legs = stackToBytes(level, equipment.get(EquipmentSlot.LEGS));
        byte[] feet = stackToBytes(level, equipment.get(EquipmentSlot.FEET));
        String uuidStr = citizenUuid.toString();
        NsukWriteExecutor.submit(() -> {
            try {
                NsukSqliteDatabase db = openDatabase(server);
                try (Connection connection = db.openConnection();
                     PreparedStatement ps = connection.prepareStatement(
                             "INSERT INTO citizen_equipment(uuid, head, chest, legs, feet) VALUES(?, ?, ?, ?, ?) "
                                     + "ON CONFLICT(uuid) DO UPDATE SET head = excluded.head, chest = excluded.chest, "
                                     + "legs = excluded.legs, feet = excluded.feet"
                     )) {
                    ps.setString(1, uuidStr);
                    ps.setBytes(2, head);
                    ps.setBytes(3, chest);
                    ps.setBytes(4, legs);
                    ps.setBytes(5, feet);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to save citizen equipment data", e);
            }
        });
    }

    public static Map<EquipmentSlot, ItemStack> load(ServerLevel level, UUID citizenUuid) {
        Map<EquipmentSlot, ItemStack> result = new EnumMap<>(EquipmentSlot.class);
        if (level == null || citizenUuid == null) return result;
        try {
            NsukSqliteDatabase db = openDatabase(level.getServer());
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
        MinecraftServer server = level.getServer();
        NsukWriteExecutor.submit(() -> {
            try {
                NsukSqliteDatabase db = openDatabase(server);
                try (Connection connection = db.openConnection();
                     PreparedStatement ps = connection.prepareStatement(
                             "DELETE FROM citizen_equipment WHERE uuid = ?")) {
                    ps.setString(1, citizenUuid.toString());
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.error("Failed to delete citizen equipment data", e);
            }
        });
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
