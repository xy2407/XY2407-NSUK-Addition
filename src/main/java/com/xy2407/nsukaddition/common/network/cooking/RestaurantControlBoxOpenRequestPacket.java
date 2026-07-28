package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 餐厅控制箱打开请求网络包，客户端请求服务端返回餐厅控制箱界面数据。 */
@SuppressWarnings("null")
public record RestaurantControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RestaurantControlBoxOpenRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(RestaurantControlBoxOpenRequestPacket::encode, RestaurantControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RestaurantControlBoxOpenRequestPacket p) { b.writeBlockPos(p.pos()); }

    public static RestaurantControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf b) { return new RestaurantControlBoxOpenRequestPacket(b.readBlockPos()); }

    public static void handle(RestaurantControlBoxOpenRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, p.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable(RestaurantConstants.TOO_FAR_MESSAGE));
            return;
        }
        PacketDistributor.sendToPlayer(player, RestaurantControlBoxOpenResponsePacket.from(RestaurantControlBoxService.buildView(level, pos)));
    }
}
