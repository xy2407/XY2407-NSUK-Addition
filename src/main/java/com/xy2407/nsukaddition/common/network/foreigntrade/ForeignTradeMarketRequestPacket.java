package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.foreigntrade.TradeItemResolver;
import com.xy2407.nsukaddition.common.foreigntrade.VillageStockService;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeVillageStockSyncPacket.StockInfo;
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

/** 客户端请求外贸市场数据，服务端返回当前浮动价格和操作权限。cityId 为当前选中的目标村城，用于构建其对应库存。 */
public record ForeignTradeMarketRequestPacket(BlockPos boxPos, String cityId) implements CustomPacketPayload {

    public static final Type<ForeignTradeMarketRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_market_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeMarketRequestPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeMarketRequestPacket::encode, ForeignTradeMarketRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeMarketRequestPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeUtf(p.cityId() != null ? p.cityId() : "", 128);
    }

    public static ForeignTradeMarketRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeMarketRequestPacket(buf.readBlockPos(), buf.readUtf(128));
    }

    public static void handle(ForeignTradeMarketRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        ForeignTradeMarket.ensureRefreshed();

        UUID targetCity = parseCityId(p.cityId());
        String villageType = targetCity != null ? VillageCityTypeStorage.getVillageType(level, targetCity) : null;
        boolean isVillage = villageType != null && !villageType.isEmpty();

        List<ForeignTradeMarket.MarketEntry> entries;
        boolean canOperate;
        if (isVillage) {
            VillageStockService.ensureVillage(level, targetCity, villageType);
            entries = ForeignTradeMarket.getMarketEntries().stream()
                    .filter(e -> villageType.equals(e.villageType()))
                    .filter(e -> VillageStockService.isVillageItem(level, targetCity, e.itemId()))
                    .toList();
            canOperate = true;
        } else {
            entries = ForeignTradeMarket.getMarketEntriesForPlayer(level, player.getUUID());
            UUID ownerCity = CityChunkManager.get(level).getChunkOwner(
                    new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
            canOperate = ownerCity != null
                    && CityService.hasPermission(level, ownerCity, player.getUUID(), CityPermissionLevel.OFFICIAL);
        }

        PacketDistributor.sendToPlayer(player,
                new ForeignTradeMarketDataPacket(p.boxPos(), entries, canOperate));
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeInventorySyncPacket(calcAvailableCounts(player, entries)));
        PacketDistributor.sendToPlayer(player, buildVillageStockPacket(level, p.cityId(), entries));
    }

    /**
     * 构建选中村城的外贸库存。直接以目标村城 cityId 读取库存，与交易包(VillageStockService.removeStock/addStock)
     * 使用同一个村城，保证购买/出售后该村城库存能被实时反映到市场卡片。
     */
    private static UUID parseCityId(String cityId) {
        if (cityId == null || cityId.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(cityId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static ForeignTradeVillageStockSyncPacket buildVillageStockPacket(ServerLevel level, String targetCityId,
                                                                      List<ForeignTradeMarket.MarketEntry> entries) {
        Map<String, StockInfo> stocks = new HashMap<>();
        if (targetCityId == null || targetCityId.isEmpty()) {
            return new ForeignTradeVillageStockSyncPacket(stocks);
        }
        UUID city;
        try {
            city = UUID.fromString(targetCityId);
        } catch (IllegalArgumentException e) {
            return new ForeignTradeVillageStockSyncPacket(stocks);
        }
        String villageType = VillageCityTypeStorage.getVillageType(level, city);
        if (villageType == null || villageType.isEmpty()) {
            return new ForeignTradeVillageStockSyncPacket(stocks);
        }
        VillageStockService.ensureVillage(level, city, villageType);
        for (ForeignTradeMarket.MarketEntry entry : entries) {
            if (entry.villageType() == null || !villageType.equals(entry.villageType())) {
                continue;
            }
            TradeItemDef def = ForeignTradeConfig.find(entry.itemId());
            if (def == null) {
                continue;
            }
            stocks.put(entry.itemId(), new StockInfo(
                    VillageStockService.villageCap(level, city, def.category()),
                    VillageStockService.getStock(level, city, entry.itemId())));
        }
        return new ForeignTradeVillageStockSyncPacket(stocks);
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
            TradeItemDef def = ForeignTradeConfig.find(entry.itemId());
            int total;
            if (def != null && def.isAnimal()) {
                total = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (TradeItemResolver.matches(s, def)) {
                        total += TradeItemResolver.countIn(s, def);
                    }
                }
            } else {
                total = warehouseCounts.getOrDefault(entry.itemId(), 0);
                ResourceLocation rl = ResourceLocation.tryParse(entry.itemId());
                Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
                if (item != null) {
                    total += player.getInventory().countItem(item);
                }
            }
            counts.put(entry.itemId(), total);
        }
        return counts;
    }
}
