package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 自由市场修改上架网络包，客户端发送修改请求，服务端更新数量和价格。 */
public record FreeMarketModifyPacket(BlockPos boxPos, long listingId, int newCount, int newPrice) implements CustomPacketPayload {

    public static final Type<FreeMarketModifyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_modify"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketModifyPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketModifyPacket::encode, FreeMarketModifyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketModifyPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeVarLong(p.listingId());
        buf.writeVarInt(p.newCount());
        buf.writeVarInt(p.newPrice());
    }

    public static FreeMarketModifyPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketModifyPacket(buf.readBlockPos(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FreeMarketModifyPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;
        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        FreeMarketRepository.FreeMarketListing listing = FreeMarketRepository.getById(p.listingId());
        if (listing == null) return;

        FreeMarketRepository.updatePriceAndCount(p.listingId(), p.newCount(), p.newPrice());

        {
            FreeMarketDataRequestPacket.sendFreeMarketData(player, level, cityId);
        }
    }
}
