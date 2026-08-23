package com.xy2407.nsukaddition.server.event;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.registry.ModItems;
import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/** 金币自动兑换城市资金：捡起金币或手持右键金币时，汇入玩家自己城市的资金。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class CoinDepositService {

    private static final double PER_COIN = 1.0D;

    private CoinDepositService() {
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!event.getItemEntity().getItem().is(ModItems.GOLD_COIN.get())) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.serverLevel();
        UUID cityId = ownedCityId(level, serverPlayer.getUUID());
        if (cityId == null) return;
        int count = event.getItemEntity().getItem().getCount();
        CityService.depositFunds(level, cityId, count * PER_COIN);
        event.setCanPickup(TriState.FALSE);
        event.getItemEntity().discard();
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.getItemStack().is(ModItems.GOLD_COIN.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.serverLevel();
        UUID cityId = ownedCityId(level, serverPlayer.getUUID());
        if (cityId == null) return;
        int count = event.getItemStack().getCount();
        CityService.depositFunds(level, cityId, count * PER_COIN);
        event.getItemStack().shrink(count);
    }

    private static UUID ownedCityId(ServerLevel level, UUID playerId) {
        return CityService.findPlayerCity(level, playerId).map(CityData::cityId).orElse(null);
    }
}