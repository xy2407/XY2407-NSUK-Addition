package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端回传单个 RTS 建筑放置注入结果:成功或失败(资金不足等),客户端据此保留/撤回临时投影。
 */
public record RtsStartBuildingResultPacket(String category, BlockPos origin, int rotation,
                                           boolean success, String reason) implements CustomPacketPayload {

    public static final Type<RtsStartBuildingResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_start_building_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsStartBuildingResultPacket> STREAM_CODEC =
            StreamCodec.of(RtsStartBuildingResultPacket::encode, RtsStartBuildingResultPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RtsStartBuildingResultPacket p) {
        b.writeUtf(p.category() != null ? p.category() : "", 64);
        b.writeBlockPos(p.origin());
        b.writeVarInt(p.rotation());
        b.writeBoolean(p.success());
        b.writeUtf(p.reason() != null ? p.reason() : "", 128);
    }

    public static RtsStartBuildingResultPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsStartBuildingResultPacket(b.readUtf(64), b.readBlockPos(), b.readVarInt(),
                b.readBoolean(), b.readUtf(128));
    }

    public static void handle(RtsStartBuildingResultPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.xy2407.nsukaddition.common.network.clientbound.RtsStartBuildingResultBridge.dispatch(p));
    }
}
