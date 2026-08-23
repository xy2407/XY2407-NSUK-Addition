package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/**
 * 人口界面的传送指令。
 * summonToPlayer = true  传送1：把该 NPC 传送到玩家位置；
 * summonToPlayer = false 传送2：把玩家传送到该 NPC 位置。
 */
public record CitizenTeleportPacket(UUID citizenUuid, boolean summonToPlayer) implements CustomPacketPayload {

    public static final Type<CitizenTeleportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "citizen_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenTeleportPacket> STREAM_CODEC =
            StreamCodec.of(CitizenTeleportPacket::encode, CitizenTeleportPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, CitizenTeleportPacket p) {
        buf.writeUUID(p.citizenUuid());
        buf.writeBoolean(p.summonToPlayer());
    }

    public static CitizenTeleportPacket decode(RegistryFriendlyByteBuf buf) {
        return new CitizenTeleportPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(CitizenTeleportPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (p.citizenUuid() == null) return;

        var playerCity = CityManager.get(level).getPlayerCity(player.getUUID());
        if (playerCity.isEmpty()) return;

        Optional<CitizenData> citizen = CitizenService.findCitizen(level, p.citizenUuid());
        if (citizen.isEmpty() || !playerCity.get().cityId().equals(citizen.get().cityId())) return;

        if (p.summonToPlayer()) {
            CitizenTeleportService.teleportCitizen(level, p.citizenUuid(), player.position());
        } else {
            CitizenEntity npc = CitizenTeleportService.findCitizenEntity(level, p.citizenUuid());
            if (npc != null) {
                player.teleportTo(npc.getX(), npc.getY(), npc.getZ());
            }
        }
    }
}