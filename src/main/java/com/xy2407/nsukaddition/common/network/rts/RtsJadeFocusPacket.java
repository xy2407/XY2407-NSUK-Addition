package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.rts.RtsJadeFocusService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 客户端→服务端：RTS 准星当前对准的实体 UUID（供 Jade 服务端距离检查放行该实体 NBT 请求，UUID.ZERO 表示清除）。 */
public record RtsJadeFocusPacket(UUID entityUuid) implements CustomPacketPayload {

    public static final Type<RtsJadeFocusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_jade_focus"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsJadeFocusPacket> STREAM_CODEC =
            StreamCodec.of(RtsJadeFocusPacket::encode, RtsJadeFocusPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsJadeFocusPacket p) {
        b.writeUUID(p.entityUuid());
    }

    public static RtsJadeFocusPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsJadeFocusPacket(b.readUUID());
    }

    public static void handle(RtsJadeFocusPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RtsJadeFocusService.setFocus(ctx.player().getUUID(), p.entityUuid()));
    }
}
