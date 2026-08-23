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

/** 自由市场"点亮黄星"切换网络包：客户端请求将某条上架标记为被广播 (highlighted)，服务端校验归属并持久化。 */
public record FreeMarketToggleStarPacket(BlockPos boxPos, long listingId) implements CustomPacketPayload {

    public static final Type<FreeMarketToggleStarPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_toggle_star"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketToggleStarPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketToggleStarPacket::encode, FreeMarketToggleStarPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketToggleStarPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeVarLong(p.listingId());
    }

    public static FreeMarketToggleStarPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketToggleStarPacket(buf.readBlockPos(), buf.readVarLong());
    }

    public static void handle(FreeMarketToggleStarPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!player.blockPosition().closerThan(p.boxPos(), 64.0D)) return;

        UUID cityId = CityChunkManager.get(level).getChunkOwner(
                new net.minecraft.world.level.ChunkPos(p.boxPos()).toLong());
        if (cityId == null) return;
        if (!CityService.hasPermission(level, cityId, player.getUUID(), CityPermissionLevel.OFFICIAL)) return;

        FreeMarketRepository.FreeMarketListing listing = FreeMarketRepository.getById(p.listingId());
        if (listing == null || !cityId.toString().equals(listing.cityId())) return;

        FreeMarketRepository.setHighlighted(p.listingId(), !listing.highlighted());
        FreeMarketDataRequestPacket.sendFreeMarketData(player, level, cityId);
    }
}