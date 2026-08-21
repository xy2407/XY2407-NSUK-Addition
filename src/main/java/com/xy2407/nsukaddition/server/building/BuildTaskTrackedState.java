package com.xy2407.nsukaddition.server.building;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 建造任务追踪状态(持久化)：每个城市唯一追踪一个建筑任务(追踪任务=正在施工的任务)，
 * 暂停按市民记录。服务端重启后恢复关闭前正在追踪的任务。
 */
public final class BuildTaskTrackedState extends SavedData {

    private static final String NAME = NsukAddition.MOD_ID + "_build_task_tracked";

    private final Map<UUID, UUID> trackedTaskByCity = new HashMap<>();
    private final Map<UUID, Set<UUID>> pausedByCity = new HashMap<>();

    public static BuildTaskTrackedState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(BuildTaskTrackedState::new, BuildTaskTrackedState::load), NAME);
    }

    public static UUID getTrackedTask(ServerLevel level, UUID cityId) {
        return get(level).trackedTaskByCity.get(cityId);
    }

    public static void setTrackedTask(ServerLevel level, UUID cityId, UUID taskId) {
        if (cityId == null || taskId == null) {
            return;
        }
        BuildTaskTrackedState state = get(level);
        state.trackedTaskByCity.put(cityId, taskId);
        state.setDirty();
    }

    public static void clearTrackedTask(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return;
        }
        BuildTaskTrackedState state = get(level);
        if (state.trackedTaskByCity.remove(cityId) != null) {
            state.setDirty();
        }
    }

    public static void remove(ServerLevel level, UUID cityId) {
        BuildTaskTrackedState state = get(level);
        boolean changed = state.trackedTaskByCity.remove(cityId) != null;
        if (state.pausedByCity.remove(cityId) != null) {
            changed = true;
        }
        if (changed) {
            state.setDirty();
        }
    }

    public static void setPaused(ServerLevel level, UUID cityId, UUID citizenId) {
        BuildTaskTrackedState state = get(level);
        state.pausedByCity.computeIfAbsent(cityId, k -> new HashSet<>()).add(citizenId);
        state.setDirty();
    }

    public static void setResumed(ServerLevel level, UUID cityId, UUID citizenId) {
        BuildTaskTrackedState state = get(level);
        Set<UUID> set = state.pausedByCity.get(cityId);
        if (set != null && set.remove(citizenId)) {
            if (set.isEmpty()) {
                state.pausedByCity.remove(cityId);
            }
            state.setDirty();
        }
    }

    public static boolean isPaused(ServerLevel level, UUID cityId, UUID citizenId) {
        Set<UUID> set = get(level).pausedByCity.get(cityId);
        return set != null && set.contains(citizenId);
    }

    public static Set<UUID> getPaused(ServerLevel level, UUID cityId) {
        Set<UUID> set = get(level).pausedByCity.get(cityId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public static Map<UUID, Set<UUID>> getPausedByCity(ServerLevel level) {
        Map<UUID, Set<UUID>> src = get(level).pausedByCity;
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : src.entrySet()) {
            copy.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return copy;
    }

    public static BuildTaskTrackedState load(CompoundTag tag, HolderLookup.Provider registries) {
        BuildTaskTrackedState state = new BuildTaskTrackedState();

        CompoundTag tracked = tag.getCompound("tracked_task");
        for (String key : tracked.getAllKeys()) {
            try {
                state.trackedTaskByCity.put(UUID.fromString(key), tracked.getUUID(key));
            } catch (Exception e) {
                NsukAddition.LOGGER.warn("BuildTaskTrackedState: 非法追踪任务记录 key={}", key, e);
            }
        }

        CompoundTag paused = tag.getCompound("paused");
        for (String cityKey : paused.getAllKeys()) {
            try {
                UUID cityId = UUID.fromString(cityKey);
                CompoundTag citizenTag = paused.getCompound(cityKey);
                Set<UUID> set = new HashSet<>();
                for (String idx : citizenTag.getAllKeys()) {
                    try {
                        set.add(citizenTag.getUUID(idx));
                    } catch (Exception e) {
                        NsukAddition.LOGGER.warn("BuildTaskTrackedState: 非法暂停记录 city={} idx={}", cityKey, idx, e);
                    }
                }
                if (!set.isEmpty()) {
                    state.pausedByCity.put(cityId, set);
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.warn("BuildTaskTrackedState: 非法暂停城市 key={}", cityKey, e);
            }
        }

        return state;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag tracked = new CompoundTag();
        for (Map.Entry<UUID, UUID> e : trackedTaskByCity.entrySet()) {
            tracked.putUUID(e.getKey().toString(), e.getValue());
        }
        tag.put("tracked_task", tracked);

        CompoundTag paused = new CompoundTag();
        for (Map.Entry<UUID, Set<UUID>> e : pausedByCity.entrySet()) {
            CompoundTag citizenTag = new CompoundTag();
            int i = 0;
            for (UUID citizenId : e.getValue()) {
                citizenTag.putUUID(String.valueOf(i++), citizenId);
            }
            paused.put(e.getKey().toString(), citizenTag);
        }
        tag.put("paused", paused);

        return tag;
    }
}
