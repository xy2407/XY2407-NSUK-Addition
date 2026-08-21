package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端→服务端：请求在玩家位置生成 RTS 假人实体。 */
public record RtsFakePlayerSpawnPacket() implements CustomPacketPayload {

    public static final Type<RtsFakePlayerSpawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_fake_player_spawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsFakePlayerSpawnPacket> STREAM_CODEC =
            StreamCodec.unit(new RtsFakePlayerSpawnPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RtsFakePlayerSpawnPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ctx.enqueueWork(() -> {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof RtsFakePlayerEntity fake && fake.getOwnerUUID().equals(player.getUUID())) {
                    fake.discard();
                }
            }
            RtsFakePlayerEntity fake = new RtsFakePlayerEntity(level, player.position(), player.getUUID());
            fake.setYRot(player.getYRot());
            fake.yBodyRot = player.getYRot();
            fake.yHeadRot = player.getYRot();
            fake.setCustomName(Component.literal(player.getGameProfile().getName()));
            fake.setCustomNameVisible(true);
            level.addFreshEntity(fake);
        });
    }
}
