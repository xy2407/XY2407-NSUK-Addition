package com.xy2407.nsukaddition.common.mining;

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

/** 采矿控制箱数据的持久化管理器（SavedData + SQLite 双存储）。 */
@SuppressWarnings("null")
public final class MiningBoxManager extends SavedData {
    private static final String DATA_NAME = MiningConstants.DATA_NAME;
    private static final Factory<MiningBoxManager> FACTORY = new Factory<>(MiningBoxManager::new, MiningBoxManager::load, null);

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NSukAddition-Mining-SQLite");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<BlockPos, MiningBoxData> boxes = new ConcurrentHashMap<>();
    private final Set<BlockPos> pendingSaves = new HashSet<>();
    private volatile ServerLevel level;
    private volatile boolean sqliteLoaded;

    public static MiningBoxManager get(ServerLevel level) {
        MiningBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;

        if (!manager.sqliteLoaded) {
            manager.loadFromSqlite();
        }
        return manager;
    }

    private static MiningBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MiningBoxManager manager = new MiningBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MiningBoxData data = MiningBoxData.fromTag(list.getCompound(i));
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
        CompoundTag sqliteData = MiningBoxSqliteStorage.loadAll(lv);
        if (sqliteData != null) {
            ListTag list = sqliteData.getList("Boxes", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                MiningBoxData data = MiningBoxData.fromTag(list.getCompound(i));
                boxes.put(data.boxPos(), data);
                if (data.autoRestock()) {
                    com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig.syncFromMiningBox(lv, data.boxPos());
                }
            }
        }
    }

    public ServerLevel level() { return level; }

    public MiningBoxData get(BlockPos pos) { return pos == null ? null : boxes.get(pos.immutable()); }

    public MiningBoxData getOrCreate(BlockPos pos) {
        return boxes.computeIfAbsent(pos.immutable(), MiningBoxData::new);
    }

    public void persist(MiningBoxData data) {
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
                MiningBoxSqliteStorage.saveBox(lv, data);
            } finally {
                synchronized (pendingSaves) {
                    pendingSaves.remove(key);
                }
            }
        });
    }

    public void remove(BlockPos pos) {
        if (pos == null) return;
        BlockPos key = pos.immutable();
        if (boxes.remove(key) != null) {
            setDirty();

            ServerLevel lv = level;
            if (lv != null) {
                IO_EXECUTOR.execute(() -> MiningBoxSqliteStorage.deleteBox(lv, key.asLong()));
            }
        }
    }

    public List<MiningBoxData> all() { return List.copyOf(boxes.values()); }
}
