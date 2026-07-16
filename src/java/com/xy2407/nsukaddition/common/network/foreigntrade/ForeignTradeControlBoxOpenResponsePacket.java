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

import java.util.UUID;

/** 外贸控制箱打开响应网络包，服务端返回界面数据供客户端渲染。 */
@SuppressWarnings("null")
public record ForeignTradeControlBoxOpenResponsePacket(
        BlockPos boxPos,
        boolean running,
        String statusKey,
        String statusText,
        String selectedTradeId,
        boolean hasWorker,
        UUID workerId,
        String workerName
) implements CustomPacketPayload {

    public static final Type<ForeignTradeControlBoxOpenResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeControlBoxOpenResponsePacket::encode, ForeignTradeControlBoxOpenResponsePacket::decode);

    public static ForeignTradeControlBoxOpenResponsePacket from(ForeignTradeBoxView view) {
        return new ForeignTradeControlBoxOpenResponsePacket(
                view.boxPos(), view.running(), view.statusKey(), view.statusText(), view.selectedTradeId(),
                view.hasWorker(), view.workerId(), view.workerName());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeControlBoxOpenResponsePacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeBoolean(p.running());
        buf.writeUtf(p.statusKey(), 128);
        buf.writeUtf(p.statusText(), 256);
        buf.writeUtf(p.selectedTradeId(), 128);
        buf.writeBoolean(p.hasWorker());
        if (p.hasWorker() && p.workerId() != null) buf.writeUUID(p.workerId());
        buf.writeUtf(p.workerName(), 128);
    }

    public static ForeignTradeControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos boxPos = buf.readBlockPos();
        boolean running = buf.readBoolean();
        String statusKey = buf.readUtf(128);
        String statusText = buf.readUtf(256);
        String selectedTradeId = buf.readUtf(128);
        boolean hasWorker = buf.readBoolean();
        UUID workerId = hasWorker ? buf.readUUID() : null;
        String workerName = buf.readUtf(128);
        return new ForeignTradeControlBoxOpenResponsePacket(boxPos, running, statusKey, statusText,
                selectedTradeId, hasWorker, workerId, workerName);
    }

    public static void handle(ForeignTradeControlBoxOpenResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ForeignTradeControlBoxBridge.open(p));
    }
}
