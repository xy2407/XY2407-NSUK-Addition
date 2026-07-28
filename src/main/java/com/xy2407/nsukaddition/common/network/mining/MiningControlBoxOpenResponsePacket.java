package com.xy2407.nsukaddition.common.network.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.MiningControlBoxUiBridge;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 挖矿控制盒打开响应网络包，携带完整视图数据回复客户端。 */
@SuppressWarnings("null")
public record MiningControlBoxOpenResponsePacket(
        BlockPos boxPos,
        boolean hasWorker,
        String workerName,
        int currentYLevel,
        boolean running,
        int workTicks,
        int maxWorkTicks,
        String statusKey,
        String statusText
) implements CustomPacketPayload {

    public static final Type<MiningControlBoxOpenResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "mining_control_box_open_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(MiningControlBoxOpenResponsePacket::encode, MiningControlBoxOpenResponsePacket::decode);

    public static void encode(RegistryFriendlyByteBuf b, MiningControlBoxOpenResponsePacket p) {
        BlockPos.STREAM_CODEC.encode(b, p.boxPos());
        b.writeBoolean(p.hasWorker());
        b.writeUtf(p.workerName());
        b.writeVarInt(p.currentYLevel());
        b.writeBoolean(p.running());
        b.writeVarInt(p.workTicks());
        b.writeVarInt(p.maxWorkTicks());
        b.writeUtf(p.statusKey());
        b.writeUtf(p.statusText());
    }

    public static MiningControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf b) {
        return new MiningControlBoxOpenResponsePacket(
                BlockPos.STREAM_CODEC.decode(b), b.readBoolean(), b.readUtf(),
                b.readVarInt(), b.readBoolean(), b.readVarInt(),
                b.readVarInt(), b.readUtf(), b.readUtf());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static MiningControlBoxOpenResponsePacket from(MiningControlBoxView view) {
        return new MiningControlBoxOpenResponsePacket(
                view.boxPos(), view.hasWorker(), view.workerName(), view.currentYLevel(),
                view.running(), view.workTicks(), view.maxWorkTicks(), view.statusKey(), view.statusText());
    }

    public static void handle(MiningControlBoxOpenResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> MiningControlBoxUiBridge.refreshActive(p));
    }
}
