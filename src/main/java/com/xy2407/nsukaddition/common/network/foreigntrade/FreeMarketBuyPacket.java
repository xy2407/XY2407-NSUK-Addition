package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.capture.CaptureContainerUtil;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
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

/** 自由市场购买网络包，客户端发送购买请求，服务端扣款、发货、删除上架记录。 */
public record FreeMarketBuyPacket(BlockPos boxPos, long listingId) implements CustomPacketPayload {

    public static final Type<FreeMarketBuyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketBuyPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketBuyPacket::encode, FreeMarketBuyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketBuyPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeVarLong(p.listingId());
    }

    public static FreeMarketBuyPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketBuyPacket(buf.readBlockPos(), buf.readVarLong());
    }

    public static void handle(FreeMarketBuyPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID buyerCityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (buyerCityId == null) return;
        if (!CityService.hasPermission(level, buyerCityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        FreeMarketRepository.FreeMarketListing listing = FreeMarketRepository.getById(p.listingId());
        if (listing == null) return;

        double price = listing.price();
        if (!EconomyService.canAfford(level, buyerCityId, price)) return;

        EconomyService.withdrawCityFunds(level, buyerCityId, player, price, "free_market_buy");

        UUID sellerCityId = null;
        try {
            sellerCityId = UUID.fromString(listing.cityId());
        } catch (IllegalArgumentException ignored) {}
        if (sellerCityId != null) {
            EconomyService.depositCityFunds(level, sellerCityId, player, price, "free_market_sell");
        }

        ItemStack tradeStack = ItemStack.EMPTY;
        String itemNbt = listing.itemNbt();
        if (itemNbt != null && !itemNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(itemNbt);
                tradeStack = ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (tradeStack.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(listing.itemId());
            if (rl == null) { return; }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) { return; }
            tradeStack = new ItemStack(item);
        }
        tradeStack.setCount(listing.count());

        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(buyerCityId);
        List<BlockPos> warehousePoses = new java.util.ArrayList<>();
        for (LogisticsWarehouseData warehouse : warehouses) {
            warehousePoses.add(warehouse.boxPos());
        }

        ItemStack deliver = tradeStack.copy();
        if (deliver.getItem() instanceof EntityCaptureItem && EntityCaptureItem.getEntityType(deliver) != null) {
            deliver = CaptureContainerUtil.mergeIntoWarehouses(level, warehousePoses, deliver);
        }
        ItemStack remaining = deliver.copy();
        for (BlockPos warehousePos : warehousePoses) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = LogisticsWarehouseInventoryService.insert(level, warehousePos, remaining);
        }
        if (!remaining.isEmpty()) {
            LogisticsWarehouseInventoryService.insertIntoPlayerInventory(player.getInventory(), remaining);
        }

        FreeMarketRepository.delete(p.listingId());

        FreeMarketDataRequestPacket.sendFreeMarketData(player, level, buyerCityId);

        ForeignTradeMarket.ensureRefreshed();
        var entries = ForeignTradeMarket.getMarketEntries();
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeInventorySyncPacket(
                        ForeignTradeMarketRequestPacket.calcAvailableCounts(player, entries)));
    }
}
