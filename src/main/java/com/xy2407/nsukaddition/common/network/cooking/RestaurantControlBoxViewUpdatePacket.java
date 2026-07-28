package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.RestaurantControlBoxBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 餐厅控制箱视图更新网络包，服务端推送餐厅控制箱界面状态变更到客户端。 */
@SuppressWarnings("null")
public record RestaurantControlBoxViewUpdatePacket(RestaurantControlBoxOpenResponsePacket response) implements CustomPacketPayload {

    public static final Type<RestaurantControlBoxViewUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_control_box_view_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantControlBoxViewUpdatePacket> STREAM_CODEC =
            StreamCodec.of((b, p) -> RestaurantControlBoxOpenResponsePacket.encode(b, p.response()),
                    b -> new RestaurantControlBoxViewUpdatePacket(RestaurantControlBoxOpenResponsePacket.decode(b)));

    public static RestaurantControlBoxViewUpdatePacket from(RestaurantControlBoxOpenResponsePacket resp) { return new RestaurantControlBoxViewUpdatePacket(resp); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RestaurantControlBoxViewUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RestaurantControlBoxBridge.refresh(p.response()));
    }
}
