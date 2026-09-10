package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.CitizenBusyBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 服务端→客户端：告知该市民正在就餐无法被召回，请人口界面给出提示。 */
public record CitizenBusyHintPacket(UUID citizenUuid) implements CustomPacketPayload {

    public static final Type<CitizenBusyHintPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "citizen_busy_hint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenBusyHintPacket> STREAM_CODEC =
            StreamCodec.of(CitizenBusyHintPacket::encode, CitizenBusyHintPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, CitizenBusyHintPacket p) {
        buf.writeUUID(p.citizenUuid());
    }

    public static CitizenBusyHintPacket decode(RegistryFriendlyByteBuf buf) {
        return new CitizenBusyHintPacket(buf.readUUID());
    }

    public static void handle(CitizenBusyHintPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CitizenBusyBridge.dispatch(p.citizenUuid()));
    }
}