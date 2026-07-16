package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.foreigntrade.ForeignTradeMenuScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端发送物流仓库物品清单到客户端。 */
public record FreeMarketWarehouseDataPacket(
        List<FreeMarketWarehouseRequestPacket.WarehouseItem> items
) implements CustomPacketPayload {

    public static final Type<FreeMarketWarehouseDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_warehouse_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketWarehouseDataPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketWarehouseDataPacket::encode, FreeMarketWarehouseDataPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketWarehouseDataPacket p) {
        buf.writeVarInt(p.items().size());
        for (var e : p.items()) {
            buf.writeUtf(e.itemId(), 128);
            buf.writeVarInt(e.count());
            buf.writeUtf(e.itemNbt() != null ? e.itemNbt() : "", 4096);
        }
    }

    public static FreeMarketWarehouseDataPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<FreeMarketWarehouseRequestPacket.WarehouseItem> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(new FreeMarketWarehouseRequestPacket.WarehouseItem(buf.readUtf(128), buf.readVarInt(), buf.readUtf(4096)));
        }
        return new FreeMarketWarehouseDataPacket(items);
    }

    public static void handle(FreeMarketWarehouseDataPacket p, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> ForeignTradeMenuScreenOpener.updateWarehouseData(p.items()));
    }
}
