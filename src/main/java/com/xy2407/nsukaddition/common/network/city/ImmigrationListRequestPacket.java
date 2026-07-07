package com.xy2407.nsukaddition.common.network.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.city.TownImmigrationService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 移民列表请求包，由客户端发送城市标识以查询该城市的待审批移民。 */
@SuppressWarnings("null")
public record ImmigrationListRequestPacket(UUID cityId) implements CustomPacketPayload {

    public static final Type<ImmigrationListRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "immigration_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImmigrationListRequestPacket> STREAM_CODEC =
            StreamCodec.of(ImmigrationListRequestPacket::encode, ImmigrationListRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ImmigrationListRequestPacket p) {
        b.writeUUID(p.cityId());
    }

    public static ImmigrationListRequestPacket decode(RegistryFriendlyByteBuf b) {
        return new ImmigrationListRequestPacket(b.readUUID());
    }

    public static void handle(ImmigrationListRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(player,
                    new ImmigrationListResponsePacket(p.cityId(), TownImmigrationService.pendingForCity(p.cityId())));
        }
    }
}
