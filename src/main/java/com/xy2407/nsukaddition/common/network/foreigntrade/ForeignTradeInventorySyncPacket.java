package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.ForeignTradeInventorySyncBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** 服务端同步各物品当前拥有数量（物流仓库+玩家背包）到客户端。 */
public record ForeignTradeInventorySyncPacket(
        Map<String, Integer> counts
) implements CustomPacketPayload {

    public static final Type<ForeignTradeInventorySyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_inventory_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeInventorySyncPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeInventorySyncPacket::encode, ForeignTradeInventorySyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeInventorySyncPacket p) {
        buf.writeVarInt(p.counts().size());
        for (var e : p.counts().entrySet()) {
            buf.writeUtf(e.getKey(), 128);
            buf.writeVarInt(e.getValue());
        }
    }

    public static ForeignTradeInventorySyncPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Integer> counts = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            counts.put(buf.readUtf(128), buf.readVarInt());
        }
        return new ForeignTradeInventorySyncPacket(counts);
    }

    public static void handle(ForeignTradeInventorySyncPacket p, IPayloadContext ctx) {
        if (!ctx.flow().isClientbound()) return;
        ctx.enqueueWork(() -> ForeignTradeInventorySyncBridge.handleSync(p.counts()));
    }
}
