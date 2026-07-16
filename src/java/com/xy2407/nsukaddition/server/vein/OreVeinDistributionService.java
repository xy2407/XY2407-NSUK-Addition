package com.xy2407.nsukaddition.server.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.vein.OreVeinChunkData;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 矿脉分布服务，基于世界种子确定性生成矿脉的位置、类型和规模。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class OreVeinDistributionService {

    private static final long SALT_CENTER   = 0x6F72655665696E43L;
    private static final long SALT_ORE_TYPE = 0x6F72655665696E54L;
    private static final long SALT_SIZE     = 0x6F72655665696E53L;
    private static final long SALT_GROW     = 0x6F72655665696E47L;

    private static final int CENTER_MOD = 4096;

    private static final int CENTER_THRESHOLD_OVERWORLD = 320;

    private static final int CENTER_THRESHOLD_NETHER = 400;

    private static final int LARGE_VEIN_CHANCE_NUMERATOR = 3;
    private static final int LARGE_VEIN_CHANCE_DENOMINATOR = 16;

    private static final int SMALL_MIN = 2;
    private static final int SMALL_MAX = 3;

    private static final int LARGE_MIN = 9;
    private static final int LARGE_MAX = 12;

    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, OreVeinChunkData>> veinCaches = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Set<Long>> blockedChunkSets = new ConcurrentHashMap<>();

    private OreVeinDistributionService() {}

    public static OreVeinChunkData getVeinAt(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null) return null;
        String dimKey = level.dimension().location().toString();
        ConcurrentHashMap<Long, OreVeinChunkData> veinCache = veinCaches.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        Set<Long> blockedChunks = blockedChunkSets.computeIfAbsent(dimKey, k -> ConcurrentHashMap.newKeySet());

        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);

        OreVeinChunkData cached = veinCache.get(chunkKey);
        if (cached != null) return cached;

        if (blockedChunks.contains(chunkKey)) return null;

        long seed = level.getSeed();
        if (!isVeinCenter(seed, chunkX, chunkZ, level.dimension())) return null;

        OreVeinType oreType = selectOreType(seed, chunkX, chunkZ, level.dimension());
        int targetSize = selectVeinSize(seed, chunkX, chunkZ);
        Set<Long> veinChunks = growVein(seed, chunkX, chunkZ, targetSize, veinCache, blockedChunks);

        if (veinChunks.isEmpty()) return null;

        long veinId = veinId(dimKey, chunkX, chunkZ);
        for (long pos : veinChunks) {
            veinCache.put(pos, new OreVeinChunkData(pos, oreType, veinId));

            markBlockedNeighbors(pos, veinCache, blockedChunks);
        }

        return veinCache.get(chunkKey);
    }

    public static Set<Long> getVeinChunks(ServerLevel level, int chunkX, int chunkZ) {
        OreVeinChunkData data = getVeinAt(level, chunkX, chunkZ);
        if (data == null) return Collections.emptySet();
        long veinId = data.veinId();
        String dimKey = level.dimension().location().toString();
        ConcurrentHashMap<Long, OreVeinChunkData> veinCache = veinCaches.get(dimKey);
        if (veinCache == null) return Collections.emptySet();

        Set<Long> result = new HashSet<>();
        for (OreVeinChunkData d : veinCache.values()) {
            if (d.veinId() == veinId) result.add(d.chunkPos());
        }
        return result;
    }

    private static boolean isVeinCenter(long worldSeed, int cx, int cz, ResourceKey<Level> dimension) {
        long h = hashPos(worldSeed, cx, cz, SALT_CENTER);
        return (h & 0xFFF) < getCenterThreshold(dimension);
    }

    private static int getCenterThreshold(ResourceKey<Level> dimension) {
        return dimension == Level.NETHER ? CENTER_THRESHOLD_NETHER : CENTER_THRESHOLD_OVERWORLD;
    }

    private static OreVeinType selectOreType(long worldSeed, int cx, int cz, ResourceKey<Level> dimension) {
        long h = hashPos(worldSeed, cx, cz, SALT_ORE_TYPE);
        int totalWeight = OreVeinType.totalWeightFor(dimension);
        int roll = totalWeight <= 0 ? 0 : (int) (h & 0x7FFFFFFF) % totalWeight;
        return OreVeinType.selectByWeight(dimension, roll);
    }

    private static int selectVeinSize(long worldSeed, int cx, int cz) {
        long h = hashPos(worldSeed, cx, cz, SALT_SIZE);
        boolean isLarge = (h & (LARGE_VEIN_CHANCE_DENOMINATOR - 1)) < LARGE_VEIN_CHANCE_NUMERATOR;
        if (isLarge) {

            int extra = (int) ((h >>> 4) & 0x3);
            return LARGE_MIN + extra;
        } else {

            int extra = (int) ((h >>> 4) & 0x1);
            return SMALL_MIN + extra;
        }
    }

    private static Set<Long> growVein(long worldSeed, int centerX, int centerZ, int targetSize,
                                       ConcurrentHashMap<Long, OreVeinChunkData> veinCache,
                                       Set<Long> blockedChunks) {
        Set<Long> vein = new LinkedHashSet<>();
        Set<Long> candidates = new LinkedHashSet<>();

        long centerKey = ChunkPos.asLong(centerX, centerZ);
        vein.add(centerKey);
        addValidNeighbors(candidates, centerX, centerZ, vein, blockedChunks, veinCache);

        long veinId = centerKey;

        while (vein.size() < targetSize && !candidates.isEmpty()) {
            long best = pickBestCandidate(worldSeed, veinId, candidates);
            candidates.remove(best);

            int cx = ChunkPos.getX(best);
            int cz = ChunkPos.getZ(best);

            if (!isIsolated(cx, cz, veinCache, blockedChunks)) continue;

            vein.add(best);
            addValidNeighbors(candidates, cx, cz, vein, blockedChunks, veinCache);
        }

        return vein;
    }

    private static void addValidNeighbors(Set<Long> candidates, int cx, int cz,
                                           Set<Long> vein, Set<Long> blockedChunks,
                                           ConcurrentHashMap<Long, OreVeinChunkData> veinCache) {
        for (int[] dir : NEIGHBOR_DIRS) {
            int nx = cx + dir[0];
            int nz = cz + dir[1];
            long key = ChunkPos.asLong(nx, nz);
            if (!vein.contains(key) && !blockedChunks.contains(key) && !veinCache.containsKey(key)) {
                candidates.add(key);
            }
        }
    }

    private static boolean isIsolated(int cx, int cz,
                                       ConcurrentHashMap<Long, OreVeinChunkData> veinCache,
                                       Set<Long> blockedChunks) {
        long self = ChunkPos.asLong(cx, cz);
        if (veinCache.containsKey(self) || blockedChunks.contains(self)) return false;
        for (int[] dir : NEIGHBOR_DIRS) {
            long neighbor = ChunkPos.asLong(cx + dir[0], cz + dir[1]);
            if (veinCache.containsKey(neighbor)) return false;

        }
        return true;
    }

    private static long pickBestCandidate(long worldSeed, long veinId, Set<Long> candidates) {
        long best = 0;
        long bestPriority = Long.MIN_VALUE;
        for (long cand : candidates) {
            int cx = ChunkPos.getX(cand);
            int cz = ChunkPos.getZ(cand);

            long priority = hashPos(worldSeed ^ veinId, cx, cz, SALT_GROW);
            if (priority > bestPriority || (priority == bestPriority && cand > best)) {
                bestPriority = priority;
                best = cand;
            }
        }
        return best;
    }

    private static long veinId(String dimKey, int centerX, int centerZ) {
        long dimPart = ((long) dimKey.hashCode()) << 32;
        long chunkPart = ChunkPos.asLong(centerX, centerZ) & 0xFFFFFFFFL;
        return dimPart | chunkPart;
    }

    private static void markBlockedNeighbors(long chunkPos,
                                              ConcurrentHashMap<Long, OreVeinChunkData> veinCache,
                                              Set<Long> blockedChunks) {
        int cx = ChunkPos.getX(chunkPos);
        int cz = ChunkPos.getZ(chunkPos);
        for (int[] dir : NEIGHBOR_DIRS) {
            long neighbor = ChunkPos.asLong(cx + dir[0], cz + dir[1]);
            if (!veinCache.containsKey(neighbor)) {
                blockedChunks.add(neighbor);
            }
        }
    }

    public static void clearDimension(String dimKey) {
        veinCaches.remove(dimKey);
        blockedChunkSets.remove(dimKey);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            clearDimension(serverLevel.dimension().location().toString());
        }
    }

    private static final int[][] NEIGHBOR_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static long hashPos(long worldSeed, int cx, int cz, long salt) {
        long h = salt;
        h ^= worldSeed;
        h = mix64(h ^ ((long) cx * 0x9E3779B97F4A7C15L));
        h = mix64(h ^ ((long) cz * 0x85EBCA77C2B2AE63L));
        return h;
    }

    private static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x = x ^ (x >>> 31);
        return x;
    }
}
