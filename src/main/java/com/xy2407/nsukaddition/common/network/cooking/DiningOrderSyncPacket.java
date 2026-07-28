package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.DiningOrderBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 就餐订单同步包（S→C），通知客户端某 NPC 点了什么菜。 */
@SuppressWarnings("null")
public record DiningOrderSyncPacket(UUID citizenId, String itemId, boolean start) implements CustomPacketPayload {

    public static final Type<DiningOrderSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "dining_order_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiningOrderSyncPacket> STREAM_CODEC =
            StreamCodec.of(DiningOrderSyncPacket::encode, DiningOrderSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, DiningOrderSyncPacket p) {
        b.writeUUID(p.citizenId());
        b.writeUtf(p.itemId(), 128);
        b.writeBoolean(p.start());
    }

    public static DiningOrderSyncPacket decode(RegistryFriendlyByteBuf b) {
        return new DiningOrderSyncPacket(b.readUUID(), b.readUtf(128), b.readBoolean());
    }

    public static void handle(DiningOrderSyncPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DiningOrderBridge.handle(p));
    }
}
