package com.xy2407.nsukaddition.server.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.UUID;

/** 附属地区块同步服务，玩家登录和切换维度时将所有附属地区块数据同步至客户端缓存。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class ColonySyncService {

    private ColonySyncService() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncColonyChunks(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncColonyChunks(player);
    }

    private static void syncColonyChunks(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String dimId = level.dimension().location().toString();
        List<UUID> colonyIds = ColonySqliteStorage.loadAllColonyIds(level, dimId);
        for (UUID colonyId : colonyIds) {
            ColonyChunkSyncPacket.sendToPlayer(player, level, colonyId);
        }
    }
}
