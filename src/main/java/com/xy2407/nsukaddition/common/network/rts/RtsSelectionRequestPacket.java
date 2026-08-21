package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** RTS 选中请求(client→server)：玩家点击/框选想选中的实体集合，服务端实时裁决(假人/本城 NPC)后回传允许集合。 */
public record RtsSelectionRequestPacket(Set<UUID> requestedIds) implements CustomPacketPayload {

    public static final Type<RtsSelectionRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_selection_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsSelectionRequestPacket> STREAM_CODEC =
            StreamCodec.of(RtsSelectionRequestPacket::encode, RtsSelectionRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsSelectionRequestPacket p) {
        b.writeInt(p.requestedIds().size());
        for (UUID id : p.requestedIds()) {
            b.writeUUID(id);
        }
    }

    public static RtsSelectionRequestPacket decode(RegistryFriendlyByteBuf b) {
        int size = b.readInt();
        Set<UUID> ids = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(b.readUUID());
        }
        return new RtsSelectionRequestPacket(ids);
    }

    public static void handle(RtsSelectionRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        ctx.enqueueWork(() -> {
            Set<UUID> allowed = new HashSet<>();
            for (UUID id : p.requestedIds()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof RtsFakePlayerEntity fake) {
                    if (fake.getOwnerUUID() != null && fake.getOwnerUUID().equals(player.getUUID())) {
                        allowed.add(id);
                    }
                } else if (entity instanceof CitizenEntity citizen
                        && RtsCityAccessValidator.canControlNpc(level, player, citizen)) {
                    allowed.add(id);
                }
            }
            PacketDistributor.sendToPlayer(player, new RtsSelectionCorrectionPacket(allowed));
        });
    }
}
