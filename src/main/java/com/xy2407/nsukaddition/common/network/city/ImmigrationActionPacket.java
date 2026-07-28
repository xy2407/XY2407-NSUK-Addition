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

/** 移民审批操作包，由客户端发送批准或拒绝指定移民请求的指令至服务端。 */
@SuppressWarnings("null")
public record ImmigrationActionPacket(UUID cityId, UUID requestId, boolean approve) implements CustomPacketPayload {

    public static final Type<ImmigrationActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "immigration_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImmigrationActionPacket> STREAM_CODEC =
            StreamCodec.of(ImmigrationActionPacket::encode, ImmigrationActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ImmigrationActionPacket p) {
        b.writeUUID(p.cityId());
        b.writeUUID(p.requestId());
        b.writeBoolean(p.approve());
    }

    public static ImmigrationActionPacket decode(RegistryFriendlyByteBuf b) {
        return new ImmigrationActionPacket(b.readUUID(), b.readUUID(), b.readBoolean());
    }

    public static void handle(ImmigrationActionPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        boolean success;
        if (p.approve()) {
            success = TownImmigrationService.approve(level, player, p.cityId(), p.requestId());
        } else {
            success = TownImmigrationService.reject(level, p.cityId(), p.requestId());
        }

        if (success) {

            PacketDistributor.sendToPlayer(player,
                    new ImmigrationListResponsePacket(p.cityId(), TownImmigrationService.pendingForCity(p.cityId())));
        }
    }
}
