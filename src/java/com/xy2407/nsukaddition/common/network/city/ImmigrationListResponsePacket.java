package com.xy2407.nsukaddition.common.network.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.ImmigrationScreenBridge;
import com.xy2407.nsukaddition.common.city.ImmigrantData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 移民列表响应包，由服务端发送指定城市的待审批移民数据至客户端。 */
@SuppressWarnings("null")
public record ImmigrationListResponsePacket(UUID cityId, List<ImmigrantData> immigrants) implements CustomPacketPayload {

    public static final Type<ImmigrationListResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "immigration_list_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImmigrationListResponsePacket> STREAM_CODEC =
            StreamCodec.of(ImmigrationListResponsePacket::encode, ImmigrationListResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ImmigrationListResponsePacket p) {
        b.writeUUID(p.cityId());
        b.writeVarInt(p.immigrants().size());
        for (ImmigrantData i : p.immigrants()) {
            b.writeUUID(i.requestId());
            b.writeUUID(i.cityId());
            b.writeUUID(i.citizenId());
            b.writeUtf(i.name());
            b.writeDouble(i.grantFunds());
            b.writeLong(i.createdDay());
        }
    }

    public static ImmigrationListResponsePacket decode(RegistryFriendlyByteBuf b) {
        UUID cityId = b.readUUID();
        int count = b.readVarInt();
        List<ImmigrantData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new ImmigrantData(
                    b.readUUID(), b.readUUID(), b.readUUID(),
                    b.readUtf(), b.readDouble(), b.readLong()
            ));
        }
        return new ImmigrationListResponsePacket(cityId, list);
    }

    public static void handle(ImmigrationListResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ImmigrationScreenBridge.refresh(p.cityId(), p.immigrants()));
    }
}
