package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityDataService;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
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

/** 自由市场上架网络包，客户端发送上架请求，服务端扣除物品并记录上架信息。 */
public record FreeMarketListPacket(BlockPos boxPos, String itemId, int count, int price, String itemNbt) implements CustomPacketPayload {

    public static final Type<FreeMarketListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketListPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketListPacket::encode, FreeMarketListPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketListPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeUtf(p.itemId(), 128);
        buf.writeVarInt(p.count());
        buf.writeVarInt(p.price());
        buf.writeUtf(p.itemNbt() != null ? p.itemNbt() : "", 4096);
    }

    public static FreeMarketListPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketListPacket(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readUtf(4096));
    }

    public static void handle(FreeMarketListPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;

        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        ItemStack tradeStack = ItemStack.EMPTY;
        String itemNbt = p.itemNbt();
        if (itemNbt != null && !itemNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(itemNbt);
                tradeStack = ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (tradeStack.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(p.itemId());
            if (rl == null) return;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) return;
            tradeStack = new ItemStack(item, 1);
        }
        Item item = tradeStack.getItem();

        int needed = p.count();
        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
        for (LogisticsWarehouseData wh : warehouses) {
            if (needed <= 0) break;
            ItemStack extracted = LogisticsWarehouseInventoryService.extract(level, wh.boxPos(), tradeStack, needed);
            if (!extracted.isEmpty()) needed -= extracted.getCount();
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
            needed = rem;
        }

        int actuallyListed = p.count() - needed;
        if (actuallyListed > 0) {
            CityData city = CityDataService.getCity(level, cityId);
            String cityName = city != null && city.cityName() != null ? city.cityName() : cityId.toString();
            FreeMarketRepository.insert(cityId.toString(), cityName, p.itemId(), actuallyListed, p.price(), player.getGameProfile().getName(), itemNbt);
        }

        FreeMarketDataRequestPacket.sendFreeMarketData(player, level, cityId);
    }
}
