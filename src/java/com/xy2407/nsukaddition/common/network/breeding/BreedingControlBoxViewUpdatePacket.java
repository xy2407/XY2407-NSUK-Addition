package com.xy2407.nsukaddition.common.network.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.BreedingControlBoxBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 繁育控制箱视图更新网络包，服务端推送繁育控制箱界面状态变更到客户端。 */
@SuppressWarnings("null")
public record BreedingControlBoxViewUpdatePacket(BreedingControlBoxOpenResponsePacket response) implements CustomPacketPayload {

    public static final Type<BreedingControlBoxViewUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "breeding_control_box_view_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreedingControlBoxViewUpdatePacket> STREAM_CODEC =
            StreamCodec.of((b, p) -> BreedingControlBoxOpenResponsePacket.encode(b, p.response()),
                    b -> new BreedingControlBoxViewUpdatePacket(BreedingControlBoxOpenResponsePacket.decode(b)));

    public static BreedingControlBoxViewUpdatePacket from(BreedingControlBoxOpenResponsePacket resp) {
        return new BreedingControlBoxViewUpdatePacket(resp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BreedingControlBoxViewUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> BreedingControlBoxBridge.refresh(p.response()));
    }
}
