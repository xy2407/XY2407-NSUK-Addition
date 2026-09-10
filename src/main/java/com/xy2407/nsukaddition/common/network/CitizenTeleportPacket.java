package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.common.restaurant.RestaurantDiningService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** 人口界面传送。传送1=把该NPC召到玩家身边；传送2=玩家传到该NPC处。缺实体时先按数据库位置加载区块找回原实体，不生成新实体防UUID冲突。 */
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

        CitizenData data = citizen.get();
        boolean sameDim = data.dimensionId() != null
                && data.dimensionId().equals(level.dimension().location().toString());

        CitizenEntity npc = CitizenTeleportService.findCitizenEntity(level, p.citizenUuid());
        if (npc == null && sameDim) {
            forceLoadRecoveryChunks(level, data);
            npc = CitizenTeleportService.findCitizenEntity(level, p.citizenUuid());
        }

        if (npc == null) {
            PacketDistributor.sendToPlayer(player, new CitizenLostHintPacket(p.citizenUuid()));
            return;
        }
        if (p.summonToPlayer()) {
            if (RestaurantDiningService.isDining(p.citizenUuid())) {
                PacketDistributor.sendToPlayer(player, new CitizenBusyHintPacket(p.citizenUuid()));
                return;
            }
            CitizenTeleportService.teleportCitizen(level, p.citizenUuid(), player.position());
        } else {
            player.teleportTo(npc.getX(), npc.getY(), npc.getZ());
        }
    }

    private static void forceLoadRecoveryChunks(ServerLevel level, CitizenData data) {
        data.lastKnownChunk().ifPresent(cp -> level.getChunk(cp.x, cp.z));
        if (data.homeId() != null) {
            CityPoiData home = CityPoiManager.get(level).getPoi(data.homeId());
            if (home != null && home.pos() != null) {
                ChunkPos cp = new ChunkPos(home.pos());
                level.getChunk(cp.x, cp.z);
            }
        }
        if (data.workplacePos() != null) {
            ChunkPos cp = new ChunkPos(data.workplacePos());
            level.getChunk(cp.x, cp.z);
        }
        CityManager.get(level).getCity(data.cityId()).ifPresent(city -> {
            BlockPos core = city.cityCorePos();
            if (core != null) {
                ChunkPos cp = new ChunkPos(core);
                level.getChunk(cp.x, cp.z);
            }
        });
    }
}