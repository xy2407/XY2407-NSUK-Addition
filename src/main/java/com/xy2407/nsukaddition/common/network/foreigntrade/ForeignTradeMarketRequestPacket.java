package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客户端请求外贸市场数据，服务端返回当前浮动价格和操作权限。 */
public record ForeignTradeMarketRequestPacket(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<ForeignTradeMarketRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_market_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeMarketRequestPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeMarketRequestPacket::encode, ForeignTradeMarketRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeMarketRequestPacket p) {
        buf.writeBlockPos(p.boxPos());
    }

    public static ForeignTradeMarketRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeMarketRequestPacket(buf.readBlockPos());
    }

    public static void handle(ForeignTradeMarketRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        ForeignTradeMarket.ensureRefreshed();
        var entries = ForeignTradeMarket.getMarketEntries();

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        boolean canOperate = cityId != null
                && CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL);

        var entriesToSend = cityId != null ? entries : List.<ForeignTradeMarket.MarketEntry>of();
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeMarketDataPacket(p.boxPos(), entriesToSend, canOperate));
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeInventorySyncPacket(calcAvailableCounts(player, entries)));
    }

    static Map<String, Integer> calcAvailableCounts(ServerPlayer player, List<ForeignTradeMarket.MarketEntry> entries) {
        Map<String, Integer> counts = new HashMap<>();
        UUID cityId = CityChunkManager.get(player.serverLevel()).getChunkOwner(player.chunkPosition().toLong());
        List<LogisticsWarehouseData> warehouses = cityId != null
                ? LogisticsManager.get(player.serverLevel()).warehouses(cityId) : List.of();

        Map<String, Integer> warehouseCounts = new HashMap<>();
        for (var wh : warehouses) {
            for (var wi : LogisticsWarehouseInventoryService.aggregate(player.serverLevel(), wh.boxPos())) {
                String id = BuiltInRegistries.ITEM.getKey(wi.displayStack().getItem()).toString();
                warehouseCounts.merge(id, wi.count(), Integer::sum);
            }
        }

        for (var entry : entries) {
            int total = warehouseCounts.getOrDefault(entry.itemId(), 0);
            ResourceLocation rl = ResourceLocation.tryParse(entry.itemId());
            Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            if (item != null) {
                total += player.getInventory().countItem(item);
            }
            counts.put(entry.itemId(), total);
        }
        return counts;
    }
}
