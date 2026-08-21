package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.RtsBuildingBoundsClearBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端→客户端：RTS 选中建筑师开始同步建筑界限前，先清空客户端已显示的建筑界限
 * （解决建筑迁移后原位置边框残留）。客户端经 bridge 调 BuildingBoundsRenderer.clearAll()。
 */
public record RtsBuildingBoundsClearPacket() implements CustomPacketPayload {

    public static final Type<RtsBuildingBoundsClearPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_building_bounds_clear"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsBuildingBoundsClearPacket> STREAM_CODEC =
            StreamCodec.of((b, p) -> {
            }, b -> new RtsBuildingBoundsClearPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RtsBuildingBoundsClearPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(RtsBuildingBoundsClearBridge::dispatch);
    }
}
