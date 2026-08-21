package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxBoundsUpdatePacket;
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

/** 客户端→服务端同步 RTS 选中实体集合，驱动市民冻结/解冻。 */
public record RtsSelectionSyncPacket(Set<UUID> selectedIds) implements CustomPacketPayload {

    public static final Type<RtsSelectionSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_selection_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsSelectionSyncPacket> STREAM_CODEC =
            StreamCodec.of(RtsSelectionSyncPacket::encode, RtsSelectionSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsSelectionSyncPacket p) {
        b.writeInt(p.selectedIds().size());
        for (UUID id : p.selectedIds()) {
            b.writeUUID(id);
        }
    }

    public static RtsSelectionSyncPacket decode(RegistryFriendlyByteBuf b) {
        int size = b.readInt();
        Set<UUID> ids = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(b.readUUID());
        }
        return new RtsSelectionSyncPacket(ids);
    }

    public static void handle(RtsSelectionSyncPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        ctx.enqueueWork(() -> {
            Set<UUID> allowed = new HashSet<>();
            for (UUID id : p.selectedIds()) {
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
            RtsCitizenTaskManager.syncSelection(allowed);
            syncCityBuildingBounds(level, player, allowed);
        });
    }

    private static void syncCityBuildingBounds(ServerLevel level, ServerPlayer player, Set<UUID> allowed) {
        UUID builderCityId = null;
        if (allowed != null) {
            for (UUID id : allowed) {
                if (!(level.getEntity(id) instanceof CitizenEntity)) {
                    continue;
                }
                CitizenData data = CitizenService.findCitizen(level, id).orElse(null);
                if (data == null || data.cityId() == null || data.jobType() != CityJobType.BUILDER) {
                    continue;
                }
                builderCityId = data.cityId();
                break;
            }
        }
        PacketDistributor.sendToPlayer(player, new RtsBuildingBoundsClearPacket());
        if (builderCityId == null) {
            return;
        }
        sendBuildingBoundsForCity(level, player, builderCityId);
    }

    public static void sendBuildingBoundsForCity(ServerLevel level, ServerPlayer player, UUID cityId) {
        if (level == null || player == null || cityId == null) {
            return;
        }
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(level)) {
            if (record == null || !cityId.equals(record.cityId())
                    || record.minPos() == null || record.maxPos() == null) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new ResidentialControlBoxBoundsUpdatePacket(
                    record.worldOrigin() != null ? record.worldOrigin() : record.minPos(),
                    true,
                    record.minPos(),
                    record.maxPos(),
                    java.util.List.of()));
        }
    }
}
