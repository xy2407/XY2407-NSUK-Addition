package com.xy2407.nsukaddition.common.network.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.mining.MiningControlBoxUiRoot;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 挖矿控制盒视图状态更新网络包，服务端向客户端推送实时状态。 */
@SuppressWarnings("null")
public record MiningControlBoxViewUpdatePacket(
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

    public static final Type<MiningControlBoxViewUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "mining_control_box_view_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningControlBoxViewUpdatePacket> STREAM_CODEC =
            StreamCodec.of(MiningControlBoxViewUpdatePacket::encode, MiningControlBoxViewUpdatePacket::decode);

    private static void encode(RegistryFriendlyByteBuf b, MiningControlBoxViewUpdatePacket p) {
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

    private static MiningControlBoxViewUpdatePacket decode(RegistryFriendlyByteBuf b) {
        return new MiningControlBoxViewUpdatePacket(
                BlockPos.STREAM_CODEC.decode(b), b.readBoolean(), b.readUtf(),
                b.readVarInt(), b.readBoolean(), b.readVarInt(),
                b.readVarInt(), b.readUtf(), b.readUtf());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static MiningControlBoxViewUpdatePacket from(MiningControlBoxView view) {
        return new MiningControlBoxViewUpdatePacket(
                view.boxPos(), view.hasWorker(), view.workerName(), view.currentYLevel(),
                view.running(), view.workTicks(), view.maxWorkTicks(), view.statusKey(), view.statusText());
    }

    public static void handle(MiningControlBoxViewUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> MiningControlBoxUiRoot.refreshActive(p));
    }
}
