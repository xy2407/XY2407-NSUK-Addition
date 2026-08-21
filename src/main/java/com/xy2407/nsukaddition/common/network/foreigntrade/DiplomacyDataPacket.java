package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;
import com.xy2407.nsukaddition.common.network.clientbound.DiplomacyDataBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端同步玩家外交关系数据到客户端，经桥接存入客户端缓存。 */
public record DiplomacyDataPacket(List<DiplomacyRelation> relations) implements CustomPacketPayload {

    public static final Type<DiplomacyDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "diplomacy_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiplomacyDataPacket> STREAM_CODEC =
            StreamCodec.of(DiplomacyDataPacket::encode, DiplomacyDataPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, DiplomacyDataPacket p) {
        buf.writeVarInt(p.relations().size());
        for (DiplomacyRelation r : p.relations()) {
            buf.writeUtf(r.villageType() != null ? r.villageType() : "", 128);
            buf.writeInt(r.posX());
            buf.writeInt(r.posZ());
            buf.writeUtf(r.cityId() != null ? r.cityId() : "", 128);
            buf.writeUtf(r.cityName() != null ? r.cityName() : "", 128);
            buf.writeVarLong(r.establishedAt());
        }
    }

    public static DiplomacyDataPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<DiplomacyRelation> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new DiplomacyRelation(
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readVarLong()
            ));
        }
        return new DiplomacyDataPacket(List.copyOf(list));
    }

    public static void handle(DiplomacyDataPacket p, IPayloadContext ctx) {
        if (!ctx.flow().isClientbound()) return;
        ctx.enqueueWork(() -> DiplomacyDataBridge.handleData(p.relations()));
    }
}
