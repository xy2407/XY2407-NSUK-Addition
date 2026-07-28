package com.xy2407.nsukaddition.client.colony;

import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenResponsePacket;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端附属地区块缓存，用于区分附属地与主城区块。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ColonyChunkClientCache {

    private static final ColonyChunkClientCache INSTANCE = new ColonyChunkClientCache();

    private final Map<Long, UUID> chunkToColony = new ConcurrentHashMap<>();
    private final Map<UUID, ColonyEntry> colonyEntries = new ConcurrentHashMap<>();

    private ColonyChunkClientCache() {}

    public static ColonyChunkClientCache getInstance() { return INSTANCE; }

    public void updateFromPacket(UUID colonyId, String colonyName, String parentCityName,
                                  List<ColonyCoreOpenResponsePacket.ChunkCoord> chunks) {
        colonyEntries.put(colonyId, new ColonyEntry(colonyName, parentCityName));
        for (ColonyCoreOpenResponsePacket.ChunkCoord cc : chunks) {
            chunkToColony.put(ChunkPos.asLong(cc.x(), cc.z()), colonyId);
        }
    }

    public void updateFromSync(UUID colonyId, String colonyName, UUID parentCityId,
                                List<ColonyCoreOpenResponsePacket.ChunkCoord> chunks) {
        colonyEntries.put(colonyId, new ColonyEntry(colonyName, null, parentCityId));
        for (ColonyCoreOpenResponsePacket.ChunkCoord cc : chunks) {
            chunkToColony.put(ChunkPos.asLong(cc.x(), cc.z()), colonyId);
        }
    }

    public void clear() {
        chunkToColony.clear();
        colonyEntries.clear();
    }

    public void removeColony(UUID colonyId) {
        colonyEntries.remove(colonyId);
        chunkToColony.entrySet().removeIf(e -> colonyId.equals(e.getValue()));
    }

    public UUID getColonyOwner(long chunkLong) {
        return chunkToColony.get(chunkLong);
    }

    public ColonyEntry getColonyEntry(UUID colonyId) {
        return colonyEntries.get(colonyId);
    }

    public int countColoniesByParentCity(UUID parentCityId) {
        if (parentCityId == null) return 0;
        int count = 0;
        for (ColonyEntry entry : colonyEntries.values()) {
            if (parentCityId.equals(entry.parentCityId())) count++;
        }
        return count;
    }

    public record ColonyEntry(String colonyName, String parentCityName, UUID parentCityId) {
        public ColonyEntry(String colonyName, String parentCityName) {
            this(colonyName, parentCityName, null);
        }
    }
}
