package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import common.cn.kafei.simukraft.economy.EconomyService;
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

import java.util.List;
import java.util.UUID;

/** 外贸交易网络包，客户端发送购买/出售请求，服务端使用当前市场浮动价格。 */
@SuppressWarnings("null")
public record ForeignTradeTransactionPacket(BlockPos boxPos, String itemId, boolean isBuy) implements CustomPacketPayload {

    public static final Type<ForeignTradeTransactionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_transaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeTransactionPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeTransactionPacket::encode, ForeignTradeTransactionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeTransactionPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeUtf(p.itemId(), 128);
        buf.writeBoolean(p.isBuy());
    }

    public static ForeignTradeTransactionPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeTransactionPacket(buf.readBlockPos(), buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(ForeignTradeTransactionPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;
        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        ForeignTradeMarket.MarketEntry marketEntry = ForeignTradeMarket.getEntry(p.itemId());
        if (marketEntry == null) return;

        ResourceLocation rl = ResourceLocation.tryParse(p.itemId());
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return;

        int count = marketEntry.count();
        double price = p.isBuy() ? marketEntry.buyPrice() : marketEntry.sellPrice();

        ItemStack tradeStack = new ItemStack(item, count);

        if (p.isBuy()) {
            if (!EconomyService.canAfford(level, cityId, price)) return;
            EconomyService.withdrawCityFunds(level, cityId, player, price, "foreign_trade_buy");

            ItemStack remaining = tradeStack.copy();
            List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
            for (LogisticsWarehouseData wh : warehouses) {
                if (remaining.isEmpty()) break;
                remaining = LogisticsWarehouseInventoryService.insert(level, wh.boxPos(), remaining);
            }
            if (!remaining.isEmpty()) {
                LogisticsWarehouseInventoryService.insertIntoPlayerInventory(player.getInventory(), remaining);
            }
        } else {
            int needed = count;
            List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
            for (LogisticsWarehouseData wh : warehouses) {
                if (needed <= 0) break;
                ItemStack extracted = LogisticsWarehouseInventoryService.extract(level, wh.boxPos(), tradeStack, needed);
                if (!extracted.isEmpty()) {
                    needed -= extracted.getCount();
                }
            }
            if (needed > 0 && player.getInventory().countItem(item) >= needed) {
                int rem = needed;
                for (int i = 0; i < player.getInventory().getContainerSize() && rem > 0; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (slot.is(item)) {
                        int toRemove = Math.min(rem, slot.getCount());
                        slot.shrink(toRemove);
                        rem -= toRemove;
                    }
                }
            }
            int actuallySold = count - needed;
            if (actuallySold > 0) {
                EconomyService.depositCityFunds(level, cityId, player,
                        price * actuallySold / count, "foreign_trade_sell");
            }
        }

        var entries = ForeignTradeMarket.getMarketEntries();
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeInventorySyncPacket(
                        ForeignTradeMarketRequestPacket.calcAvailableCounts(player, entries)));
    }
}
