package com.xy2407.nsukaddition.common.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * 医院餐食订单持久化（SavedData）：
 * 保存医生侧的病患点餐状态，防止服务器重启后丢失——
 * 1. orderTargets：病患 → 订单目标（餐厅位置/菜品/价格/城市/日期/下单 tick）；
 * 2. doctorQueues：医生 → 待配送病患队列（单医生串行配送）；
 * 3. orderedToday：病患 → 当日已下单标记。
 * 餐厅侧订单本身由 RestaurantBoxData.toTag 持久化（含病患点的菜），本类只补医生配送侧状态。
 */
@SuppressWarnings("null")
public final class HospitalMealOrderSavedData extends SavedData {
    private static final String DATA_NAME = "xy2407_nsuk_addition_hospital_meal_orders";
    private static final Factory<HospitalMealOrderSavedData> FACTORY =
            new Factory<>(HospitalMealOrderSavedData::new, HospitalMealOrderSavedData::load, null);

    private final ConcurrentMap<UUID, HospitalMealOrderService.OrderTarget> orderTargets = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Deque<UUID>> doctorQueues = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> orderedToday = new ConcurrentHashMap<>();

    private HospitalMealOrderSavedData() {
    }

    public static HospitalMealOrderSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static HospitalMealOrderSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HospitalMealOrderSavedData data = new HospitalMealOrderSavedData();
        ListTag targets = tag.getList("OrderTargets", Tag.TAG_COMPOUND);
        for (int i = 0; i < targets.size(); i++) {
            CompoundTag t = targets.getCompound(i);
            UUID patientId = t.getUUID("PatientId");
            if (patientId == null) continue;
            data.orderTargets.put(patientId, HospitalMealOrderService.OrderTarget.fromTag(t));
        }
        ListTag queues = tag.getList("DoctorQueues", Tag.TAG_COMPOUND);
        for (int i = 0; i < queues.size(); i++) {
            CompoundTag q = queues.getCompound(i);
            UUID doctorId = q.getUUID("DoctorId");
            if (doctorId == null) continue;
            Deque<UUID> list = new ConcurrentLinkedDeque<>();
            ListTag patients = q.getList("Patients", Tag.TAG_COMPOUND);
            for (int j = 0; j < patients.size(); j++) {
                UUID pid = patients.getCompound(j).getUUID("P");
                if (pid != null) list.addLast(pid);
            }
            if (!list.isEmpty()) data.doctorQueues.put(doctorId, list);
        }
        ListTag today = tag.getList("OrderedToday", Tag.TAG_COMPOUND);
        for (int i = 0; i < today.size(); i++) {
            CompoundTag t = today.getCompound(i);
            UUID pid = t.getUUID("PatientId");
            if (pid != null) data.orderedToday.put(pid, t.getLong("Day"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag targets = new ListTag();
        orderTargets.forEach((patientId, target) -> {
            CompoundTag t = target.toTag();
            t.putUUID("PatientId", patientId);
            targets.add(t);
        });
        tag.put("OrderTargets", targets);

        ListTag queues = new ListTag();
        doctorQueues.forEach((doctorId, list) -> {
            CompoundTag q = new CompoundTag();
            q.putUUID("DoctorId", doctorId);
            ListTag patients = new ListTag();
            for (UUID pid : list) {
                CompoundTag p = new CompoundTag();
                p.putUUID("P", pid);
                patients.add(p);
            }
            q.put("Patients", patients);
            queues.add(q);
        });
        tag.put("DoctorQueues", queues);

        ListTag today = new ListTag();
        orderedToday.forEach((pid, day) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("PatientId", pid);
            t.putLong("Day", day);
            today.add(t);
        });
        tag.put("OrderedToday", today);
        return tag;
    }

    public ConcurrentMap<UUID, HospitalMealOrderService.OrderTarget> orderTargets() {
        return orderTargets;
    }

    public ConcurrentMap<UUID, Deque<UUID>> doctorQueues() {
        return doctorQueues;
    }

    public ConcurrentMap<UUID, Long> orderedToday() {
        return orderedToday;
    }

    public void markChanged() {
        setDirty();
    }
}
