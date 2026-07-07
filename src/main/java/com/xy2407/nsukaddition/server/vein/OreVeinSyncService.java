package com.xy2407.nsukaddition.server.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.vein.OreVeinChunkSyncPacket;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 矿脉数据同步服务，在玩家登录和切换维度时将已发现矿脉数据同步至客户端。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class OreVeinSyncService {

    private OreVeinSyncService() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncDiscovered(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncDiscovered(player);
    }

    private static void syncDiscovered(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD && player.level().dimension() != Level.NETHER) return;
        OreVeinDiscoveryService.loadFromPlayer(player);
        ServerLevel level = player.serverLevel();
        Map<Long, OreVeinType> veins = OreVeinDiscoveryService.getDiscoveredVeins(player, level);
        if (veins.isEmpty()) return;

        List<OreVeinChunkSyncPacket.Entry> entries = new ArrayList<>(veins.size());
        for (Map.Entry<Long, OreVeinType> e : veins.entrySet()) {
            entries.add(new OreVeinChunkSyncPacket.Entry(e.getKey(), e.getValue()));
        }
        PacketDistributor.sendToPlayer(player, new OreVeinChunkSyncPacket(entries));
    }
}
