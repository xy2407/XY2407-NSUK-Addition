package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.foreigntrade.ForeignTradeMenuScreenOpener;
import com.xy2407.nsukaddition.common.foreigntrade.FreeMarketRepository;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端发送自由市场数据到客户端，包含本城市和其他城市的上架列表。 */
public record FreeMarketDataPacket(
        List<FreeMarketRepository.FreeMarketListing> ownCityListings,
        List<FreeMarketRepository.FreeMarketListing> otherCityListings
) implements CustomPacketPayload {

    public static final Type<FreeMarketDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "free_market_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMarketDataPacket> STREAM_CODEC =
            StreamCodec.of(FreeMarketDataPacket::encode, FreeMarketDataPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, FreeMarketDataPacket p) {
        encodeList(buf, p.ownCityListings());
        encodeList(buf, p.otherCityListings());
    }

    public static FreeMarketDataPacket decode(RegistryFriendlyByteBuf buf) {
        return new FreeMarketDataPacket(decodeList(buf), decodeList(buf));
    }

    private static void encodeList(RegistryFriendlyByteBuf buf, List<FreeMarketRepository.FreeMarketListing> list) {
        buf.writeVarInt(list.size());
        for (var e : list) {
            buf.writeVarLong(e.id());
            buf.writeUtf(e.cityId(), 128);
            buf.writeUtf(e.cityName(), 128);
            buf.writeUtf(e.itemId(), 128);
            buf.writeVarInt(e.count());
            buf.writeVarInt(e.price());
            buf.writeUtf(e.sellerPlayer(), 64);
            buf.writeVarLong(e.createdAt());
            buf.writeUtf(e.itemNbt() != null ? e.itemNbt() : "", 4096);
        }
    }

    private static List<FreeMarketRepository.FreeMarketListing> decodeList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<FreeMarketRepository.FreeMarketListing> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new FreeMarketRepository.FreeMarketListing(
                    buf.readVarLong(),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readVarLong(),
                    buf.readUtf(4096)
            ));
        }
        return list;
    }

    public static void handle(FreeMarketDataPacket p, IPayloadContext ctx) {
        if (!ctx.flow().isClientbound()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> ForeignTradeMenuScreenOpener.updateFreeMarketData(p.ownCityListings(), p.otherCityListings()));
    }
}
