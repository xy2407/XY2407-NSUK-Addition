package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeBoxView;
import com.xy2407.nsukaddition.common.network.clientbound.ForeignTradeControlBoxBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 外贸控制箱打开响应网络包，服务端返回界面数据供客户端渲染。 */
@SuppressWarnings("null")
public record ForeignTradeControlBoxOpenResponsePacket(
        BlockPos boxPos,
        boolean running,
        String statusKey,
        String statusText,
        String selectedTradeId
) implements CustomPacketPayload {

    public static final Type<ForeignTradeControlBoxOpenResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeControlBoxOpenResponsePacket::encode, ForeignTradeControlBoxOpenResponsePacket::decode);

    public static ForeignTradeControlBoxOpenResponsePacket from(ForeignTradeBoxView view) {
        return new ForeignTradeControlBoxOpenResponsePacket(
                view.boxPos(), view.running(), view.statusKey(), view.statusText(), view.selectedTradeId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeControlBoxOpenResponsePacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeBoolean(p.running());
        buf.writeUtf(p.statusKey(), 128);
        buf.writeUtf(p.statusText(), 256);
        buf.writeUtf(p.selectedTradeId(), 128);
    }

    public static ForeignTradeControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeControlBoxOpenResponsePacket(
                buf.readBlockPos(), buf.readBoolean(), buf.readUtf(128), buf.readUtf(256), buf.readUtf(128));
    }

    public static void handle(ForeignTradeControlBoxOpenResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ForeignTradeControlBoxBridge.open(p));
    }
}
