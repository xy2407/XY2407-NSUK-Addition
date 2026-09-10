package com.xy2407.nsukaddition.server.planning;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 规划任务手动暂停状态(持久化)：记录被玩家在侧边栏手动暂停的规划师，服务端重启后保持暂停。
 */
public final class PlanningPauseState extends SavedData {

    private static final String NAME = NsukAddition.MOD_ID + "_planning_pause";

    private final Set<UUID> pausedCitizens = new HashSet<>();

    public static PlanningPauseState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(PlanningPauseState::new, PlanningPauseState::load), NAME);
    }

    public static boolean isPaused(ServerLevel level, UUID citizenId) {
        return citizenId != null && get(level).pausedCitizens.contains(citizenId);
    }

    public static void setPaused(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        PlanningPauseState state = get(level);
        if (state.pausedCitizens.add(citizenId)) {
            state.setDirty();
        }
    }

    public static void setResumed(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        PlanningPauseState state = get(level);
        if (state.pausedCitizens.remove(citizenId)) {
            state.setDirty();
        }
    }

    public static PlanningPauseState load(CompoundTag tag, HolderLookup.Provider registries) {
        PlanningPauseState state = new PlanningPauseState();
        CompoundTag paused = tag.getCompound("paused");
        for (String key : paused.getAllKeys()) {
            try {
                state.pausedCitizens.add(paused.getUUID(key));
            } catch (Exception e) {
                NsukAddition.LOGGER.warn("PlanningPauseState: 非法暂停记录 key={}", key, e);
            }
        }
        return state;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag paused = new CompoundTag();
        int i = 0;
        for (UUID citizenId : pausedCitizens) {
            paused.putUUID(String.valueOf(i++), citizenId);
        }
        tag.put("paused", paused);
        return tag;
    }
}