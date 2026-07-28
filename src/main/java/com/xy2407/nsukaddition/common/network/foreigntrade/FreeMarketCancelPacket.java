package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
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

/** 自由市场取消上架网络包，客户端发送取消请求，服务端归还物品并删除记录。 */
public record FreeMarketCancelPacket(BlockPos boxPos, long listingId) implements CustomPacketPayload {

    public static final Type<FreeMarketCancelPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketCancelPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketCancelPacket::encode, FreeMarketCancelPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketCancelPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeVarLong(p.listingId());
    }

    public static FreeMarketCancelPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketCancelPacket(buf.readBlockPos(), buf.readVarLong());
    }

    public static void handle(FreeMarketCancelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;
        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        FreeMarketRepository.FreeMarketListing listing = FreeMarketRepository.getById(p.listingId());
        if (listing == null) return;

        ItemStack returnStack = ItemStack.EMPTY;
        String itemNbt = listing.itemNbt();
        if (itemNbt != null && !itemNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(itemNbt);
                returnStack = ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (returnStack.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(listing.itemId());
            if (rl == null) return;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) return;
            returnStack = new ItemStack(item);
        }
        returnStack.setCount(listing.count());

        {
            List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
            for (LogisticsWarehouseData wh : warehouses) {
                if (returnStack.isEmpty()) break;
                returnStack = LogisticsWarehouseInventoryService.insert(level, wh.boxPos(), returnStack);
            }
        }
        if (!returnStack.isEmpty()) {
            LogisticsWarehouseInventoryService.insertIntoPlayerInventory(player.getInventory(), returnStack);
        }

        FreeMarketRepository.delete(p.listingId());

        if (cityId != null) {
            FreeMarketDataRequestPacket.sendFreeMarketData(player, level, cityId);
        }
    }
}
