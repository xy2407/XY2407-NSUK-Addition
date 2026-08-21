package com.xy2407.nsukaddition.server.city;

import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityData;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;
import com.xy2407.nsukaddition.common.network.city.CityCorePositionsPacket;
import com.xy2407.nsukaddition.common.network.city.CityCorePositionsPacket.CoreInfo;
import com.xy2407.nsukaddition.common.network.foreigntrade.DiplomacyDataPacket;
import com.xy2407.nsukaddition.common.storage.NsukWriteExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 玩家登录时同步城市核心位置和建交关系到客户端，切换维度时同步核心位置。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class CityCorePositionsSync {

    private CityCorePositionsSync() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sendPositionsToPlayer(player);
        sendDiplomacyToClient(player);
    }

    private static void sendDiplomacyToClient(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID playerUuid = player.getUUID();
        NsukWriteExecutor.submit(() -> {
            List<DiplomacyRelation> relations = DiplomacyStorage.loadRelations(level, playerUuid);
            PacketDistributor.sendToPlayer(player, new DiplomacyDataPacket(relations));
        });
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sendPositionsToPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DiplomacyStorage.invalidateCache(player.getUUID());
    }

    public static void sendPositionsToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        CityManager cityManager = CityManager.get(level);
        String dimensionId = level.dimension().location().toString();
        UUID playerId = player.getUUID();

        List<CoreInfo> cores = new ArrayList<>();
        for (CityData city : cityManager.allCities()) {
            if (!city.dimensionId().equals(dimensionId)) continue;
            BlockPos pos = city.cityCorePos();
            if (pos == null || pos.equals(BlockPos.ZERO)) continue;
            boolean mine = cityManager.hasPermission(city.cityId(), playerId, CityPermissionLevel.MAYOR);
            cores.add(new CoreInfo(pos, mine));
        }

        PacketDistributor.sendToPlayer(player, new CityCorePositionsPacket(cores));
    }
}