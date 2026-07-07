package com.xy2407.nsukaddition.server.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.vein.OreVeinChunkData;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 矿脉发现服务，管理玩家已发现的矿脉记录及持久化存储。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class OreVeinDiscoveryService {

    private static final ConcurrentHashMap<String, Set<Long>> DISCOVERED_VEINS = new ConcurrentHashMap<>();

    private static final String NBT_KEY = "nsukaddition_discovered_veins";

    private OreVeinDiscoveryService() {}

    public static Map<Long, OreVeinType> discover(ServerPlayer player, ServerLevel level, int chunkX, int chunkZ) {
        OreVeinChunkData data = OreVeinDistributionService.getVeinAt(level, chunkX, chunkZ);
        if (data == null) return Collections.emptyMap();

        Set<Long> discovered = getDiscovered(player);
        discovered.add(data.veinId());
        saveToPlayer(player);

        Map<Long, OreVeinType> result = new HashMap<>();
        for (long pos : OreVeinDistributionService.getVeinChunks(level, chunkX, chunkZ)) {
            OreVeinChunkData chunkData = OreVeinDistributionService.getVeinAt(level, ChunkPos.getX(pos), ChunkPos.getZ(pos));
            if (chunkData != null) {
                result.put(pos, chunkData.oreType());
            }
        }
        return result;
    }

    public static Map<Long, OreVeinType> getDiscoveredVeins(ServerPlayer player, ServerLevel level) {
        Set<Long> discovered = getDiscovered(player);
        if (discovered.isEmpty()) return Collections.emptyMap();

        String dimKey = level.dimension().location().toString();
        int dimHash = dimKey.hashCode();
        Map<Long, OreVeinType> result = new HashMap<>();
        for (long veinId : discovered) {

            if ((int) (veinId >>> 32) != dimHash) continue;
            long chunkKey = veinId & 0xFFFFFFFFL;
            int cx = ChunkPos.getX(chunkKey);
            int cz = ChunkPos.getZ(chunkKey);
            for (long pos : OreVeinDistributionService.getVeinChunks(level, cx, cz)) {
                OreVeinChunkData data = OreVeinDistributionService.getVeinAt(level, ChunkPos.getX(pos), ChunkPos.getZ(pos));
                if (data != null) {
                    result.put(pos, data.oreType());
                }
            }
        }
        return result;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        loadFromPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        loadFromPlayer(player);
    }

    private static Set<Long> getDiscovered(ServerPlayer player) {
        return DISCOVERED_VEINS.computeIfAbsent(player.getStringUUID(), k -> new HashSet<>());
    }

    public static void loadFromPlayer(ServerPlayer player) {
        Set<Long> set = new HashSet<>();
        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(NBT_KEY, CompoundTag.TAG_LIST)) {
            ListTag list = persistent.getList(NBT_KEY, CompoundTag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof LongTag longTag) {
                    set.add(longTag.getAsLong());
                }
            }
        }
        DISCOVERED_VEINS.put(player.getStringUUID(), set);
    }

    private static void saveToPlayer(ServerPlayer player) {
        Set<Long> set = DISCOVERED_VEINS.get(player.getStringUUID());
        if (set == null) return;
        ListTag list = new ListTag();
        for (long id : set) {
            list.add(LongTag.valueOf(id));
        }
        player.getPersistentData().put(NBT_KEY, list);
    }
}
