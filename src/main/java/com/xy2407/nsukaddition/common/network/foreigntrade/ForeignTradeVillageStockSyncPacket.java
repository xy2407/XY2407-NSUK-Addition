package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.ForeignTradeVillageStockSyncBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** 服务端同步当前村庄城市的物品库存(上限/当前)到客户端，供卡片悬停显示。 */
public record ForeignTradeVillageStockSyncPacket(Map<String, StockInfo> stocks) implements CustomPacketPayload {

    public record StockInfo(int cap, int current) {
    }

    public static final Type<ForeignTradeVillageStockSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_village_stock_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeVillageStockSyncPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeVillageStockSyncPacket::encode, ForeignTradeVillageStockSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeVillageStockSyncPacket p) {
        buf.writeVarInt(p.stocks().size());
        for (var e : p.stocks().entrySet()) {
            buf.writeUtf(e.getKey(), 128);
            buf.writeVarInt(e.getValue().cap());
            buf.writeVarInt(e.getValue().current());
        }
    }

    public static ForeignTradeVillageStockSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, StockInfo> stocks = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            stocks.put(buf.readUtf(128), new StockInfo(buf.readVarInt(), buf.readVarInt()));
        }
        return new ForeignTradeVillageStockSyncPacket(stocks);
    }

    public static void handle(ForeignTradeVillageStockSyncPacket p, IPayloadContext ctx) {
        if (!ctx.flow().isClientbound()) return;
        ctx.enqueueWork(() -> ForeignTradeVillageStockSyncBridge.handleSync(p.stocks()));
    }
}