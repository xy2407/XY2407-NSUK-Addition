package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConstants;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeControlBoxService;
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

/** 外贸控制箱打开请求网络包，客户端请求服务端返回界面数据。 */
@SuppressWarnings("null")
public record ForeignTradeControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ForeignTradeControlBoxOpenRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeControlBoxOpenRequestPacket::encode, ForeignTradeControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ForeignTradeControlBoxOpenRequestPacket p) {
        b.writeBlockPos(p.pos());
    }

    public static ForeignTradeControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf b) {
        return new ForeignTradeControlBoxOpenRequestPacket(b.readBlockPos());
    }

    public static void handle(ForeignTradeControlBoxOpenRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, p.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable(ForeignTradeConstants.TOO_FAR_MESSAGE));
            return;
        }
        PacketDistributor.sendToPlayer(player,
                ForeignTradeControlBoxOpenResponsePacket.from(ForeignTradeControlBoxService.buildView(level, pos)));
    }
}
