package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.foreigntrade.ForeignTradeMenuScreenOpener;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端发送当前外贸市场数据（含浮动价格和操作权限）到客户端。 */
public record ForeignTradeMarketDataPacket(
        BlockPos boxPos,
        List<ForeignTradeMarket.MarketEntry> entries,
        boolean canOperate
) implements CustomPacketPayload {

    public static final Type<ForeignTradeMarketDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_market_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeMarketDataPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeMarketDataPacket::encode, ForeignTradeMarketDataPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeMarketDataPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeVarInt(p.entries().size());
        for (var e : p.entries()) {
            buf.writeUtf(e.itemId(), 128);
            buf.writeVarInt(e.count());
            buf.writeVarInt(e.buyPrice());
            buf.writeVarInt(e.sellPrice());
            buf.writeUtf(e.category(), 64);
        }
        buf.writeBoolean(p.canOperate());
    }

    public static ForeignTradeMarketDataPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos boxPos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<ForeignTradeMarket.MarketEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String itemId = buf.readUtf(128);
            int count = buf.readVarInt();
            int buyPrice = buf.readVarInt();
            int sellPrice = buf.readVarInt();
            String category = buf.readUtf(64);
            entries.add(new ForeignTradeMarket.MarketEntry(itemId, count, buyPrice, sellPrice, category));
        }
        boolean canOperate = buf.readBoolean();
        return new ForeignTradeMarketDataPacket(boxPos, entries, canOperate);
    }

    public static void handle(ForeignTradeMarketDataPacket p, IPayloadContext ctx) {
        if (!ctx.flow().isClientbound()) return;
        ForeignTradeMenuScreenOpener.openWithMarketData(p.boxPos(), p.entries(), p.canOperate());
    }
}
