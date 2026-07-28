package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 客户端请求自由市场数据，服务端返回本城市和其他城市的上架列表。 */
public record FreeMarketDataRequestPacket(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<FreeMarketDataRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_data_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketDataRequestPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketDataRequestPacket::encode, FreeMarketDataRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketDataRequestPacket p) {
        buf.writeBlockPos(p.boxPos());
    }

    public static FreeMarketDataRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketDataRequestPacket(buf.readBlockPos());
    }

    public static void handle(FreeMarketDataRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;

        sendFreeMarketData(player, level, cityId);
    }

    public static void sendFreeMarketData(ServerPlayer player, ServerLevel level, UUID cityId) {
        var ownListings = FreeMarketRepository.getByCity(cityId.toString());
        var otherListings = FreeMarketRepository.getOtherCities(cityId.toString());
        PacketDistributor.sendToPlayer(player, new FreeMarketDataPacket(ownListings, otherListings));
    }
}
