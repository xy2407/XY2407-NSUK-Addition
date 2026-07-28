package com.xy2407.nsukaddition.common.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/** 市民装备服务，负责实体装备与 SQLite 存储之间的双向同步。 */
@SuppressWarnings("null")
public final class CitizenEquipmentService {

    private CitizenEquipmentService() {}

    public static Map<EquipmentSlot, ItemStack> readFromEntity(CitizenEntity entity) {
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                equipment.put(slot, entity.getItemBySlot(slot).copy());
            }
        }
        return equipment;
    }

    public static void applyToEntity(ServerLevel level, CitizenEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        Map<EquipmentSlot, ItemStack> equipment = CitizenEquipmentSqliteStorage.load(level, entity.getUUID());
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ItemStack stack = entry.getValue();
            if (slot != null && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && stack != null && !stack.isEmpty()) {
                entity.setItemSlot(slot, stack);
            }
        }
    }

    public static void saveFromEntity(ServerLevel level, CitizenEntity entity) {
        if (level == null || entity == null) return;
        CitizenEquipmentSqliteStorage.save(level, entity.getUUID(), readFromEntity(entity));
    }

    public static void dropEquipment(ServerLevel level, CitizenEntity entity) {
        if (level == null || entity == null) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                entity.spawnAtLocation(stack.copy());
                entity.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        CitizenEquipmentSqliteStorage.delete(level, entity.getUUID());
    }
}
