package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 餐厅女仆雇佣列表请求网络包，客户端请求服务端返回玩家已驯服的女仆候选列表。
 */
@SuppressWarnings("null")
public record RestaurantMaidHireRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RestaurantMaidHireRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_maid_hire_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantMaidHireRequestPacket> STREAM_CODEC =
            StreamCodec.of(RestaurantMaidHireRequestPacket::encode, RestaurantMaidHireRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RestaurantMaidHireRequestPacket p) { b.writeBlockPos(p.pos()); }

    public static RestaurantMaidHireRequestPacket decode(RegistryFriendlyByteBuf b) { return new RestaurantMaidHireRequestPacket(b.readBlockPos()); }

    public static void handle(RestaurantMaidHireRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            RestaurantMaidHireResponsePacket.sendTo(level, player, p.pos());
        }
    }
}
