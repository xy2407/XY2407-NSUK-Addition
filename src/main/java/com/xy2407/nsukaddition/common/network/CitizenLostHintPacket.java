package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.CitizenLostBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 服务端→客户端：通知该市民实体已遗失（数据库残留），请用人口界面的【传送1】找回。 */
public record CitizenLostHintPacket(UUID citizenUuid) implements CustomPacketPayload {

    public static final Type<CitizenLostHintPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "citizen_lost_hint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenLostHintPacket> STREAM_CODEC =
            StreamCodec.of(CitizenLostHintPacket::encode, CitizenLostHintPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, CitizenLostHintPacket p) {
        buf.writeUUID(p.citizenUuid());
    }

    public static CitizenLostHintPacket decode(RegistryFriendlyByteBuf buf) {
        return new CitizenLostHintPacket(buf.readUUID());
    }

    public static void handle(CitizenLostHintPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CitizenLostBridge.dispatch(p.citizenUuid()));
    }
}