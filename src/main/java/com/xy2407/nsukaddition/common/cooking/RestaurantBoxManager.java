package com.xy2407.nsukaddition.common.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 餐厅控制箱管理器，基于 SavedData 管理所有餐厅箱数据并提供异步 SQLite 持久化。 */
@SuppressWarnings("null")
public final class RestaurantBoxManager extends SavedData {
    private static final String DATA_NAME = RestaurantConstants.DATA_NAME;
    private static final Factory<RestaurantBoxManager> FACTORY = new Factory<>(RestaurantBoxManager::new, RestaurantBoxManager::load, null);

    private final ConcurrentMap<BlockPos, RestaurantBoxData> boxes = new ConcurrentHashMap<>();
    private final Set<BlockPos> pendingSaves = ConcurrentHashMap.newKeySet();
    private volatile ServerLevel level;
    private volatile boolean sqliteLoaded;

    public static RestaurantBoxManager get(ServerLevel level) {
        RestaurantBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        if (!manager.sqliteLoaded) {
            manager.loadFromSqlite();
        }
        return manager;
    }

    private static RestaurantBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        RestaurantBoxManager manager = new RestaurantBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            RestaurantBoxData data = RestaurantBoxData.fromTag(list.getCompound(i));
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
        }
        CompoundTag sqliteData = RestaurantBoxSqliteStorage.loadAll(lv);
        if (sqliteData != null) {
            ListTag list = sqliteData.getList("Boxes", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                RestaurantBoxData data = RestaurantBoxData.fromTag(list.getCompound(i));
                boxes.put(data.boxPos(), data);
            }
        }
        sqliteLoaded = true;
    }

    public ServerLevel level() { return level; }
    public RestaurantBoxData get(BlockPos boxPos) { return boxPos == null ? null : boxes.get(boxPos.immutable()); }

    public RestaurantBoxData getOrCreate(BlockPos boxPos) {
        return boxes.computeIfAbsent(boxPos.immutable(), RestaurantBoxData::new);
    }

    public void persist(RestaurantBoxData data) {
        if (data == null) return;
        data.touch();
        boxes.put(data.boxPos(), data);
        setDirty();

        ServerLevel lv = level;
        if (lv == null) return;
        BlockPos key = data.boxPos().immutable();
        if (!pendingSaves.add(key)) return;
        RestaurantBoxSqliteStorage.saveBox(lv, data, () -> pendingSaves.remove(key));
    }

    public void remove(BlockPos boxPos) {
        if (boxPos == null) return;
        BlockPos key = boxPos.immutable();
        if (boxes.remove(key) != null) {
            setDirty();
            ServerLevel lv = level;
            if (lv != null) {
                RestaurantBoxSqliteStorage.deleteBox(lv, key.asLong());
            }
        }
    }

    public List<RestaurantBoxData> all() { return List.copyOf(boxes.values()); }
}
