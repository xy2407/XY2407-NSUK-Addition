package com.xy2407.nsukaddition.server.building;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** 建造任务追踪状态，持久化存储每个城市当前追踪和暂停的居民建造任务。 */
public final class BuildTaskTrackedState extends SavedData {

    private static final String NAME = NsukAddition.MOD_ID + "_build_task_tracked";

    private final Map<UUID, UUID> trackedByCity = new HashMap<>();
    private final Map<UUID, Set<UUID>> pausedByCity = new HashMap<>();

    public static BuildTaskTrackedState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(BuildTaskTrackedState::new, BuildTaskTrackedState::load), NAME);
    }

    public static void set(ServerLevel level, UUID cityId, UUID citizenId) {
        BuildTaskTrackedState state = get(level);
        state.trackedByCity.put(cityId, citizenId);
        state.setDirty();
    }

    public static UUID get(ServerLevel level, UUID cityId) {
        return get(level).trackedByCity.get(cityId);
    }

    public static void remove(ServerLevel level, UUID cityId) {
        BuildTaskTrackedState state = get(level);
        boolean changed = state.trackedByCity.remove(cityId) != null;
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

        CompoundTag tracked = tag.getCompound("tracked");
        for (String key : tracked.getAllKeys()) {
            try {
                state.trackedByCity.put(UUID.fromString(key), tracked.getUUID(key));
            } catch (Exception ignored) {
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
                    } catch (Exception ignored) {
                    }
                }
                if (!set.isEmpty()) {
                    state.pausedByCity.put(cityId, set);
                }
            } catch (Exception ignored) {
            }
        }

        return state;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag tracked = new CompoundTag();
        for (Map.Entry<UUID, UUID> e : trackedByCity.entrySet()) {
            tracked.putUUID(e.getKey().toString(), e.getValue());
        }
        tag.put("tracked", tracked);

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
