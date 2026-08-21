package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端→服务端：退出 RTS 模式时清除假人并传送玩家到假人位置。 */
public record RtsPlayerTeleportPacket(Vec3 target) implements CustomPacketPayload {

    public static final Type<RtsPlayerTeleportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_player_teleport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsPlayerTeleportPacket> STREAM_CODEC =
            StreamCodec.of(RtsPlayerTeleportPacket::encode, RtsPlayerTeleportPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsPlayerTeleportPacket p) {
        b.writeDouble(p.target().x);
        b.writeDouble(p.target().y);
        b.writeDouble(p.target().z);
    }

    public static RtsPlayerTeleportPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsPlayerTeleportPacket(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }

    public static void handle(RtsPlayerTeleportPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ctx.enqueueWork(() -> {
            for (ServerLevel sl : player.getServer().getAllLevels()) {
                for (var entity : sl.getAllEntities()) {
                    if (entity instanceof RtsFakePlayerEntity fake && fake.getOwnerUUID().equals(player.getUUID())) {
                        fake.discard();
                    }
                }
            }
            player.teleportTo(p.target().x, p.target().y, p.target().z);
        });
    }
}
