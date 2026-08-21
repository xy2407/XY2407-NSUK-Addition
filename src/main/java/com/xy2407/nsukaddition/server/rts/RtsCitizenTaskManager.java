package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.combat.CitizenCombatService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 服务端 RTS 市民任务管理器：跟踪冻结状态，分配和执行任务，支持扩展新任务类型。 */
public final class RtsCitizenTaskManager {

    private static final ResourceLocation RTS_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_speed");
    private static final double RTS_SPEED_BONUS = 0.2D;

    private static final Map<UUID, RtsTask> activeTasks = new ConcurrentHashMap<>();

    private static final Set<UUID> frozenCitizens = ConcurrentHashMap.newKeySet();

    private RtsCitizenTaskManager() {
    }

    public static void applyRtsSpeed(CitizenEntity citizen, boolean active) {
        if (citizen == null) return;
        var attr = citizen.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        if (active) {
            if (attr.getModifier(RTS_SPEED_MODIFIER_ID) == null) {
                attr.addTransientModifier(new AttributeModifier(RTS_SPEED_MODIFIER_ID,
                        RTS_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            attr.removeModifier(RTS_SPEED_MODIFIER_ID);
        }
    }

    public static void freezeCitizen(UUID id) {
        if (id != null) frozenCitizens.add(id);
    }

    public static void unfreezeCitizen(UUID id) {
        if (id == null) return;
        frozenCitizens.remove(id);
        RtsTask task = activeTasks.remove(id);
        if (task != null) task.onCancel();
    }

    public static boolean isFrozen(UUID id) {
        return id != null && frozenCitizens.contains(id);
    }

    public static void assignTask(UUID id, RtsTask task) {
        if (id == null || task == null) return;
        freezeCitizen(id);
        RtsTask old = activeTasks.put(id, task);
        if (old != null) old.onCancel();
    }

    public static boolean hasActiveTask(UUID id) {
        return id != null && activeTasks.containsKey(id);
    }

    public static void cancelActiveTask(UUID id) {
        if (id == null) return;
        RtsTask task = activeTasks.remove(id);
        if (task != null) task.onCancel();
    }

    public static void syncSelection(Set<UUID> selected) {
        if (selected == null) {
            return;
        }
        for (UUID id : new java.util.ArrayList<>(frozenCitizens)) {
            if (!selected.contains(id) && !activeTasks.containsKey(id)) {
                frozenCitizens.remove(id);
                CitizenCombatService.clearCommandTarget(id);
            }
        }
        for (UUID id : selected) {
            frozenCitizens.add(id);
        }
    }

    public static void tick(ServerLevel level) {
        if (activeTasks.isEmpty()) return;
        Iterator<Map.Entry<UUID, RtsTask>> it = activeTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, RtsTask> entry = it.next();
            UUID id = entry.getKey();
            RtsTask task = entry.getValue();
            if (task.isComplete()) {
                task.onCancel();
                it.remove();
                frozenCitizens.remove(id);
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity instanceof CitizenEntity citizen) {
                task.tick(citizen);
            }
        }
    }

    public static void tickFor(CitizenEntity citizen) {
        if (citizen == null) return;
        UUID id = citizen.getUUID();
        RtsTask task = activeTasks.get(id);
        if (task == null) return;
        if (task.isComplete()) {
            task.onCancel();
            activeTasks.remove(id);
            frozenCitizens.remove(id);
            return;
        }
        task.tick(citizen);
    }

    public static void clearAll() {
        for (RtsTask task : activeTasks.values()) {
            task.onCancel();
        }
        activeTasks.clear();
        frozenCitizens.clear();
    }
}