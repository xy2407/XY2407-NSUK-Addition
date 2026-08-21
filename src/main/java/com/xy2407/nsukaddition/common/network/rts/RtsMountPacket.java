package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import com.xy2407.nsukaddition.server.rts.RtsMountTask;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** RTS 上马/下马命令：选中 NPC 右键有鞍无乘客的马 → 走过去骑乘；右键自己骑乘的马 → 下马。 */
public record RtsMountPacket(UUID npcId, UUID mountId, boolean mount) implements CustomPacketPayload {

    public static final Type<RtsMountPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_mount"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsMountPacket> STREAM_CODEC =
            StreamCodec.of(RtsMountPacket::encode, RtsMountPacket::decode);

    public static void encode(RegistryFriendlyByteBuf b, RtsMountPacket p) {
        b.writeUUID(p.npcId());
        b.writeUUID(p.mountId());
        b.writeBoolean(p.mount());
    }

    public static RtsMountPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsMountPacket(b.readUUID(), b.readUUID(), b.readBoolean());
    }

    public static void handle(RtsMountPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            Entity npc = level.getEntity(p.npcId());
            if (!(npc instanceof CitizenEntity citizen)) return;
            if (!RtsCityAccessValidator.canControlNpc(level, player, citizen)) return;
            Entity mount = level.getEntity(p.mountId());
            if (!(mount instanceof AbstractHorse horse) || horse.isRemoved()) return;

            if (p.mount()) {
                if (citizen.getVehicle() == horse) return;
                if (!horse.isSaddled() || horse.isVehicle()) return;
                RtsCitizenTaskManager.assignTask(p.npcId(), new RtsMountTask(p.mountId()));
            } else {
                if (citizen.getVehicle() == horse) {
                    citizen.stopRiding();
                }
            }
        });
    }
}
