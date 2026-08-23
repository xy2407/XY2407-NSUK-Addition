package com.xy2407.nsukaddition.server.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/** 自由市场"点亮黄星"广播：每 12 分钟(14400 tick)对所有在线玩家广播一次被点亮的上架商品。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class FreeMarketHighlightBroadcastServerTick {

    private static final int CYCLE_TICKS = 14400;
    private static long tickCounter = 0;

    private FreeMarketHighlightBroadcastServerTick() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < CYCLE_TICKS) {
            return;
        }
        tickCounter = 0;

        ServerLevel level = event.getServer().overworld();
        if (level == null || event.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        List<FreeMarketRepository.FreeMarketListing> all = FreeMarketRepository.getOtherCities("");
        for (FreeMarketRepository.FreeMarketListing listing : all) {
            if (!listing.highlighted()) {
                continue;
            }
            String itemName = resolveItemName(listing.itemId());
            String coords = resolveCityCoords(level, listing.cityId());
            Component message = Component.literal(listing.cityName() + "城市，坐标" + coords + "，出售商品" + itemName);
            event.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static String resolveCityCoords(ServerLevel level, String cityId) {
        try {
            UUID uuid = UUID.fromString(cityId);
            BlockPos core = CityManager.get(level).getCity(uuid).map(CityData::cityCorePos).orElse(null);
            if (core != null) {
                return core.getX() + ", " + core.getY() + ", " + core.getZ();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "?";
    }

    private static String resolveItemName(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            return itemId;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return itemId;
        }
        return item.getDefaultInstance().getHoverName().getString();
    }
}