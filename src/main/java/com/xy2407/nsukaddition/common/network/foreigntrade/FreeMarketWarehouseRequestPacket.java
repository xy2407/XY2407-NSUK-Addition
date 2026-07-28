package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客户端请求物流仓库物品清单，服务端返回仓库聚合数据。 */
public record FreeMarketWarehouseRequestPacket(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<FreeMarketWarehouseRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_warehouse_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketWarehouseRequestPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketWarehouseRequestPacket::encode, FreeMarketWarehouseRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketWarehouseRequestPacket p) {
        buf.writeBlockPos(p.boxPos());
    }

    public static FreeMarketWarehouseRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketWarehouseRequestPacket(buf.readBlockPos());
    }

    public static void handle(FreeMarketWarehouseRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;
        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        Map<String, WarehouseItem> warehouseItems = new HashMap<>();
        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
        for (var wh : warehouses) {
            for (var wi : LogisticsWarehouseInventoryService.aggregate(level, wh.boxPos())) {
                ItemStack stack = wi.displayStack();
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                String nbt = "";
                if (!stack.isEmpty()) {
                    var tag = stack.save(level.registryAccess());
                    nbt = tag.toString();
                }
                String key = id + "\0" + nbt;
                final String finalId = id;
                final String finalNbt = nbt;
                warehouseItems.merge(key, new WarehouseItem(finalId, wi.count(), finalNbt),
                        (a, b) -> new WarehouseItem(finalId, a.count() + b.count(), finalNbt));
            }
        }

        List<WarehouseItem> items = new ArrayList<>(warehouseItems.values());

        PacketDistributor.sendToPlayer(player, new FreeMarketWarehouseDataPacket(items));
    }

    public record WarehouseItem(String itemId, int count, String itemNbt) {}
}
