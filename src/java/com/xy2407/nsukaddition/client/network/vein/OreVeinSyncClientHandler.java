package com.xy2407.nsukaddition.client.network.vein;

import com.xy2407.nsukaddition.client.vein.OreVeinClientCache;
import com.xy2407.nsukaddition.common.network.vein.OreVeinChunkSyncPacket;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/** 矿脉区块同步包的客户端处理，将区块矿脉数据写入缓存并持久化到磁盘。 */
@OnlyIn(Dist.CLIENT)
public final class OreVeinSyncClientHandler {

    private OreVeinSyncClientHandler() {}

    public static void handle(OreVeinChunkSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        ResourceKey<Level> dimension = minecraft.level.dimension();
        Map<Long, OreVeinType> veins = new HashMap<>();
        for (OreVeinChunkSyncPacket.Entry entry : packet.entries()) {
            if (entry.oreType() != null) {
                veins.put(entry.chunkPos(), entry.oreType());
            }
        }
        OreVeinClientCache.getInstance().addVeins(dimension, veins);

        OreVeinClientCache.getInstance().saveToDisk();
    }
}
