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
import common.cn.kafei.simukraft.material.GenericContainerAccess;
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
import java.util.function.Predicate;

/** 外贸交易网络包，客户端发送购买/出售请求，服务端使用当前市场浮动价格，支持批量数量。 */
@SuppressWarnings("null")
public record ForeignTradeTransactionPacket(BlockPos boxPos, String itemId, boolean isBuy, int amount) implements CustomPacketPayload {

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
        buf.writeVarInt(p.amount());
    }

    public static ForeignTradeTransactionPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeTransactionPacket(buf.readBlockPos(), buf.readUtf(128), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(ForeignTradeTransactionPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;
        int amount = Math.max(1, p.amount());

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

        int baseCount = marketEntry.count();
        int totalCount = baseCount * amount;
        double unitPrice = p.isBuy() ? marketEntry.buyPrice() : marketEntry.sellPrice();
        double totalPrice = unitPrice * amount;

        ItemStack tradeStack = new ItemStack(item, totalCount);

        if (p.isBuy()) {
            if (!EconomyService.canAfford(level, cityId, totalPrice)) return;
            EconomyService.withdrawCityFunds(level, cityId, player, totalPrice, "foreign_trade_buy");

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
            List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
            int warehouseExtracted = 0;
            Predicate<ItemStack> typeMatcher = stack -> stack.is(item);
            for (int batch = 0; batch < amount; batch++) {
                int need = baseCount;
                for (LogisticsWarehouseData wh : warehouses) {
                    if (need <= 0) break;
                    for (BlockPos container : usableContainersReflect(level, wh.containers())) {
                        if (need <= 0) break;
                        for (var snap : GenericContainerAccess.snapshotSlots(level, container)) {
                            if (need <= 0) break;
                            if (!snap.stack().isEmpty() && snap.stack().is(item)) {
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

            int playerExtracted = 0;
            int needed = totalCount - warehouseExtracted;
            if (needed > 0) {
                int rem = needed;
                for (int i = 0; i < player.getInventory().getContainerSize() && rem > 0; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (slot.is(item)) {
                        int toRemove = Math.min(rem, slot.getCount());
                        slot.shrink(toRemove);
                        rem -= toRemove;
                    }
                }
                playerExtracted = needed - rem;
            }

            int actuallySold = warehouseExtracted + playerExtracted;
            if (actuallySold > 0) {
                EconomyService.depositCityFunds(level, cityId, player,
                        totalPrice * actuallySold / totalCount, "foreign_trade_sell");
            }
        }

        var entries = ForeignTradeMarket.getMarketEntries();
        PacketDistributor.sendToPlayer(player,
                new ForeignTradeInventorySyncPacket(
                        ForeignTradeMarketRequestPacket.calcAvailableCounts(player, entries)));
    }

    /** 反射调 LogisticsWarehouseInventoryService.usableContainers，避免重写 SophisticatedStorage 子箱去重逻辑。 */
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
