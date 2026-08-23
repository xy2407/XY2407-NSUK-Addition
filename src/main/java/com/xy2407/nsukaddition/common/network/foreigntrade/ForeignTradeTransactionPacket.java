package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.capture.CaptureContainerUtil;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.foreigntrade.TradeItemResolver;
import com.xy2407.nsukaddition.common.foreigntrade.TradeQuotaService;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import com.xy2407.nsukaddition.common.foreigntrade.VillageStockService;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** 外贸交易网络包，客户端发送购买/出售请求，服务端校验建交关系与配额后执行，失败时右上角 toast 提示原因。 */
@SuppressWarnings("null")
public record ForeignTradeTransactionPacket(BlockPos boxPos, String cityId, String itemId, boolean isBuy, int amount) implements CustomPacketPayload {

    public static final Type<ForeignTradeTransactionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_transaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeTransactionPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeTransactionPacket::encode, ForeignTradeTransactionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeTransactionPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeUtf(p.cityId() != null ? p.cityId() : "", 128);
        buf.writeUtf(p.itemId() != null ? p.itemId() : "", 128);
        buf.writeBoolean(p.isBuy());
        buf.writeVarInt(p.amount());
    }

    public static ForeignTradeTransactionPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeTransactionPacket(buf.readBlockPos(), buf.readUtf(128), buf.readUtf(128), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(ForeignTradeTransactionPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;
        int amount = Math.max(1, p.amount());

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.no_city");
            return;
        }
        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.not_official");
            return;
        }

        String cityIdStr = p.cityId() != null ? p.cityId() : "";
        if (!isDiplomacyEstablished(level, player.getUUID(), cityIdStr)) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.no_diplomacy");
            return;
        }

        UUID tradeCityUuid;
        try {
            tradeCityUuid = UUID.fromString(cityIdStr);
        } catch (IllegalArgumentException e) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.invalid_city");
            return;
        }
        String villageType = VillageCityTypeStorage.getVillageType(level, tradeCityUuid);
        if (villageType == null) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.no_village_type");
            return;
        }

        ForeignTradeMarket.MarketEntry marketEntry = ForeignTradeMarket.getEntry(villageType, p.itemId());
        if (marketEntry == null) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.no_market_entry");
            return;
        }
        VillageStockService.ensureVillage(level, tradeCityUuid, villageType);

        TradeItemDef def = ForeignTradeConfig.find(p.itemId());
        if (def == null) {
            fail(player, "message.xy2407_nsuk_addition.foreign_trade.no_definition");
            return;
        }
        VillageStockService.ensureItemTradable(level, tradeCityUuid, p.itemId(), def.category());

        int baseCount = marketEntry.count();
        int totalCount = baseCount * amount;
        if (p.isBuy()) {
            int remainingQuota = TradeQuotaService.getRemainingBuyQuota(level, player.getUUID(), cityIdStr, p.itemId());
            if (remainingQuota < amount) {
                fail(player, "message.xy2407_nsuk_addition.foreign_trade.buy_quota");
                return;
            }
            if (!VillageStockService.canBuy(level, tradeCityUuid, p.itemId())) {
                fail(player, "message.xy2407_nsuk_addition.foreign_trade.out_of_stock");
                return;
            }
        } else {
            int remainingQuota = TradeQuotaService.getRemainingSellQuota(level, player.getUUID(), cityIdStr, p.itemId());
            if (remainingQuota < amount) {
                fail(player, "message.xy2407_nsuk_addition.foreign_trade.sell_quota");
                return;
            }
            if (!VillageStockService.canSell(level, tradeCityUuid, p.itemId(), def.category())) {
                fail(player, "message.xy2407_nsuk_addition.foreign_trade.sell_full");
                return;
            }
        }

        double unitPrice = p.isBuy() ? marketEntry.buyPrice() : marketEntry.sellPrice();
        double totalPrice = unitPrice * amount;

        ItemStack tradeStack = TradeItemResolver.deliver(def, totalCount);
        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
        List<BlockPos> warehousePoses = new java.util.ArrayList<>();
        for (LogisticsWarehouseData wh : warehouses) {
            warehousePoses.add(wh.boxPos());
        }

        if (p.isBuy()) {
            if (!EconomyService.canAfford(level, cityId, totalPrice)) {
                fail(player, "message.xy2407_nsuk_addition.foreign_trade.no_funds");
                return;
            }
            EconomyService.withdrawCityFunds(level, cityId, player, totalPrice, "foreign_trade_buy");

            ItemStack deliver = tradeStack.copy();
            if (deliver.getItem() instanceof EntityCaptureItem && EntityCaptureItem.getEntityType(deliver) != null) {
                deliver = CaptureContainerUtil.mergeIntoWarehouses(level, warehousePoses, deliver);
                if (!deliver.isEmpty() && EntityCaptureItem.getEntryCount(deliver) > 0) {
                    EntityType<?> type = EntityCaptureItem.getEntityType(deliver);
                    boolean baby = EntityCaptureItem.isBaby(deliver);
                    int count = EntityCaptureItem.getEntryCount(deliver);
                    int remaining = EntityCaptureItem.distributeCapture(
                            player.getInventory().items, new ItemStack(deliver.getItem()), type, baby, count);
                    if (remaining > 0) {
                        ItemStack leftover = EntityCaptureItem.createCapture(
                                new ItemStack(deliver.getItem()), type, baby, remaining);
                        if (!player.addItem(leftover) && !leftover.isEmpty()) {
                            player.drop(leftover, false);
                        }
                    }
                }
            } else {
                ItemStack remaining = tradeStack.copy();
                for (BlockPos whPos : warehousePoses) {
                    if (remaining.isEmpty()) break;
                    remaining = LogisticsWarehouseInventoryService.insert(level, whPos, remaining);
                }
                if (!remaining.isEmpty()) {
                    LogisticsWarehouseInventoryService.insertIntoPlayerInventory(player.getInventory(), remaining);
                }
            }
            TradeQuotaService.recordBuy(level, player.getUUID(), cityIdStr, p.itemId(), amount);
            VillageStockService.removeStock(level, tradeCityUuid, p.itemId(), amount);
            pushVillageStock(level, player, cityIdStr);
        } else {
            int warehouseExtracted = 0;
            if (!def.isAnimal()) {
                Predicate<ItemStack> typeMatcher = stack -> TradeItemResolver.matches(stack, def);
                for (int batch = 0; batch < amount; batch++) {
                    int need = baseCount;
                    for (LogisticsWarehouseData wh : warehouses) {
                        if (need <= 0) break;
                        for (BlockPos container : usableContainersReflect(level, wh.containers())) {
                            if (need <= 0) break;
                            for (var snap : GenericContainerAccess.snapshotSlots(level, container)) {
                                if (need <= 0) break;
                                if (!snap.stack().isEmpty() && TradeItemResolver.matches(snap.stack(), def)) {
                                    int toTake = Math.min(need, snap.stack().getCount());
                                    ItemStack extracted = GenericContainerAccess.extractFromSlot(
                                            level, container, snap.slot(), snap.access(), snap.side(),
                                            toTake, typeMatcher);
                                    if (!extracted.isEmpty()) {
                                        need -= extracted.getCount();
                                    }
                                }
                            }
                        }
                    }
                    warehouseExtracted += baseCount - need;
                    if (need == baseCount) break;
                }
            }

            int playerExtracted = 0;
            int needed = totalCount - warehouseExtracted;
            if (needed > 0) {
                int rem = needed;
                for (int i = 0; i < player.getInventory().getContainerSize() && rem > 0; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (TradeItemResolver.matches(slot, def)) {
                        int removedNow = TradeItemResolver.removeFrom(slot, def, rem);
                        rem -= removedNow;
                    }
                }
                playerExtracted = needed - rem;
            }

            int actuallySold = warehouseExtracted + playerExtracted;
            int soldBatches = baseCount > 0 ? actuallySold / baseCount : 0;
            if (actuallySold > 0) {
                EconomyService.depositCityFunds(level, cityId, player,
                        totalPrice * actuallySold / totalCount, "foreign_trade_sell");
                if (soldBatches > 0) {
                    TradeQuotaService.recordSell(level, player.getUUID(), cityIdStr, p.itemId(), soldBatches);
                    VillageStockService.addStock(level, tradeCityUuid, p.itemId(), def.category(), soldBatches);
                    pushVillageStock(level, player, cityIdStr);
                }
            } else {
                fail(player, "message.xy2407_nsuk_addition.foreign_trade.sell_nothing");
                return;
            }
        }

        var entries = ForeignTradeMarket.getMarketEntriesForPlayer(level, player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeInventorySyncPacket(
                        ForeignTradeMarketRequestPacket.calcAvailableCounts(player, entries)));
    }

    private static void pushVillageStock(ServerLevel level, ServerPlayer player, String cityIdStr) {
        PacketDistributor.sendToPlayer(player, ForeignTradeMarketRequestPacket.buildVillageStockPacket(
                level, cityIdStr,
                ForeignTradeMarket.getMarketEntriesForPlayer(level, player.getUUID())));
    }

    private static void fail(ServerPlayer player, String langKey) {
        if (player == null || langKey == null) return;
        InfoToastService.warning(player, Component.translatable(langKey));
    }

    private static boolean isDiplomacyEstablished(ServerLevel level, UUID playerUuid, String cityId) {
        if (cityId == null || cityId.isEmpty()) return false;
        var relations = DiplomacyStorage.loadRelations(level, playerUuid);
        if (relations.isEmpty()) return false;
        for (var r : relations) {
            if (cityId.equals(r.cityId())) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> usableContainersReflect(ServerLevel level, List<BlockPos> containers) {
        try {
            var method = LogisticsWarehouseInventoryService.class.getDeclaredMethod("usableContainers", ServerLevel.class, List.class);
            method.setAccessible(true);
            return (List<BlockPos>) method.invoke(null, level, containers);
        } catch (Exception e) {
            return containers;
        }
    }
}