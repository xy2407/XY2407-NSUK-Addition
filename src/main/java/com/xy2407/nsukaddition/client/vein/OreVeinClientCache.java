package com.xy2407.nsukaddition.client.vein;

import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 矿脉客户端缓存，按维度存储区块矿脉类型，支持磁盘读写和版本追踪。 */
@OnlyIn(Dist.CLIENT)
public final class OreVeinClientCache {

    private static final OreVeinClientCache INSTANCE = new OreVeinClientCache();

    private final ConcurrentHashMap<ResourceKey<Level>, ConcurrentHashMap<Long, OreVeinType>> veinsByDimension = new ConcurrentHashMap<>();
    private volatile int dataVersion;
    private volatile boolean dirty;

    private OreVeinClientCache() {}

    public static OreVeinClientCache getInstance() {
        return INSTANCE;
    }

    public void clear() {
        veinsByDimension.clear();
        dataVersion++;
    }

    public void clear(ResourceKey<Level> dimension) {
        ConcurrentHashMap<Long, OreVeinType> removed = veinsByDimension.remove(dimension);
        if (removed != null) dataVersion++;
    }

    public void addVeins(ResourceKey<Level> dimension, Map<Long, OreVeinType> veins) {
        if (dimension == null || veins == null || veins.isEmpty()) return;
        ConcurrentHashMap<Long, OreVeinType> map = veinsByDimension.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        int added = 0;
        for (Map.Entry<Long, OreVeinType> e : veins.entrySet()) {
            if (map.putIfAbsent(e.getKey(), e.getValue()) == null) {
                added++;
            }
        }
        if (added > 0) {
            dataVersion++;
            dirty = true;
        }
    }

    public OreVeinType getVein(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ConcurrentHashMap<Long, OreVeinType> map = veinsByDimension.get(dimension);
        if (map == null) return null;
        return map.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public boolean hasVein(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return getVein(dimension, chunkX, chunkZ) != null;
    }

    public boolean regionHasVeins(ResourceKey<Level> dimension, int regionX, int regionZ) {
        ConcurrentHashMap<Long, OreVeinType> map = veinsByDimension.get(dimension);
        if (map == null || map.isEmpty()) return false;
        for (long chunkPos : map.keySet()) {
            int cx = ChunkPos.getX(chunkPos);
            int cz = ChunkPos.getZ(chunkPos);
            if ((cx >> 5) == regionX && (cz >> 5) == regionZ) return true;
        }
        return false;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public void saveToDisk() {
        if (!dirty) return;
        OreVeinDiskStorage.saveVeins(veinsByDimension);
        dirty = false;
    }

    public void forceSaveToDisk() {
        OreVeinDiskStorage.saveVeins(veinsByDimension);
        dirty = false;
    }

    public void loadFromDisk() {
        Map<ResourceKey<Level>, Map<Long, OreVeinType>> loaded = OreVeinDiskStorage.loadVeins();
        for (Map.Entry<ResourceKey<Level>, Map<Long, OreVeinType>> e : loaded.entrySet()) {
            addVeins(e.getKey(), e.getValue());
        }

        dirty = false;
    }
}
