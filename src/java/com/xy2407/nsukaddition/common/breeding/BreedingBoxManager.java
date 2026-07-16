package com.xy2407.nsukaddition.common.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** 繁殖控制箱管理器，基于 SavedData 管理所有繁殖箱数据并提供异步 SQLite 持久化。 */
@SuppressWarnings("null")
public final class BreedingBoxManager extends SavedData {
    private static final String DATA_NAME = BreedingConstants.DATA_NAME;
    private static final Factory<BreedingBoxManager> FACTORY = new Factory<>(BreedingBoxManager::new, BreedingBoxManager::load, null);

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NSukAddition-Breeding-SQLite");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<BlockPos, BreedingBoxData> boxes = new ConcurrentHashMap<>();
    private final Set<BlockPos> pendingSaves = new HashSet<>();
    private volatile ServerLevel level;
    private volatile boolean sqliteLoaded;

    public static BreedingBoxManager get(ServerLevel level) {
        BreedingBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        if (!manager.sqliteLoaded) {
            manager.loadFromSqlite();
        }
        return manager;
    }

    private static BreedingBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        BreedingBoxManager manager = new BreedingBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BreedingBoxData data = BreedingBoxData.fromTag(list.getCompound(i));
            manager.boxes.put(data.boxPos(), data);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        boxes.values().forEach(data -> list.add(data.toTag()));
        tag.put("Boxes", list);
        return tag;
    }

    private void loadFromSqlite() {
        ServerLevel lv = level;
        if (lv == null) return;
        synchronized (this) {
            if (sqliteLoaded) return;
            sqliteLoaded = true;
        }
        CompoundTag sqliteData = BreedingBoxSqliteStorage.loadAll(lv);
        if (sqliteData != null) {
            ListTag list = sqliteData.getList("Boxes", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                BreedingBoxData data = BreedingBoxData.fromTag(list.getCompound(i));
                boxes.put(data.boxPos(), data);
            }
        }
    }

    public ServerLevel level() {
        return level;
    }

    public BreedingBoxData get(BlockPos boxPos) {
        return boxPos == null ? null : boxes.get(boxPos.immutable());
    }

    public BreedingBoxData getOrCreate(BlockPos boxPos) {
        return boxes.computeIfAbsent(boxPos.immutable(), BreedingBoxData::new);
    }

    public void persist(BreedingBoxData data) {
        if (data == null) return;
        data.touch();
        boxes.put(data.boxPos(), data);
        setDirty();

        ServerLevel lv = level;
        if (lv == null) return;
        BlockPos key = data.boxPos().immutable();
        synchronized (pendingSaves) {
            if (!pendingSaves.add(key)) return;
        }
        IO_EXECUTOR.execute(() -> {
            try {
                BreedingBoxSqliteStorage.saveBox(lv, data);
            } finally {
                synchronized (pendingSaves) {
                    pendingSaves.remove(key);
                }
            }
        });
    }

    public void remove(BlockPos boxPos) {
        if (boxPos == null) return;
        BlockPos key = boxPos.immutable();
        if (boxes.remove(key) != null) {
            setDirty();
            ServerLevel lv = level;
            if (lv != null) {
                IO_EXECUTOR.execute(() -> BreedingBoxSqliteStorage.deleteBox(lv, key.asLong()));
            }
        }
    }

    public List<BreedingBoxData> all() {
        return List.copyOf(boxes.values());
    }
}
