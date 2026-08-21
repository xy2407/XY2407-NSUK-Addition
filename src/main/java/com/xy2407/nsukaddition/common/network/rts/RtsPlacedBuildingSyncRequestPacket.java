package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户端请求:选中建筑师/规划师时,请求该城市已放置建筑列表用于渲染建筑界限。
 */
public record RtsPlacedBuildingSyncRequestPacket(UUID citizenId) implements CustomPacketPayload {

    public static final Type<RtsPlacedBuildingSyncRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_placed_building_sync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsPlacedBuildingSyncRequestPacket> STREAM_CODEC =
            StreamCodec.of(RtsPlacedBuildingSyncRequestPacket::encode, RtsPlacedBuildingSyncRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RtsPlacedBuildingSyncRequestPacket p) {
        b.writeUUID(p.citizenId());
    }

    public static RtsPlacedBuildingSyncRequestPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsPlacedBuildingSyncRequestPacket(b.readUUID());
    }

    public static void handle(RtsPlacedBuildingSyncRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ctx.enqueueWork(() -> {
            java.util.Optional<CitizenData> citizenOpt = CitizenService.findCitizen(level, p.citizenId());
            if (citizenOpt.isEmpty() || citizenOpt.get().cityId() == null) {
                return;
            }
            sendCityBuildingSync(level, player, citizenOpt.get().cityId());
        });
    }

    public static void sendCityBuildingSync(ServerLevel level, ServerPlayer player, UUID cityId) {
        if (level == null || player == null || cityId == null) {
            return;
        }
        List<RtsPlacedBuildingSyncPacket.Entry> entries = new ArrayList<>();
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(level)) {
            if (record == null || !cityId.equals(record.cityId())) {
                continue;
            }
            entries.add(new RtsPlacedBuildingSyncPacket.Entry(
                    record.buildingId(), record.category(), record.buildingFileName(),
                    record.minPos(), record.maxPos(), record.worldOrigin(),
                    rotationFromFacing(record.facing())));
        }
        java.util.Set<Long> chunks = CityChunkManager.get(level).getCityChunks(cityId);
        List<Long> chunkList = chunks == null ? List.of() : List.copyOf(chunks);
        PacketDistributor.sendToPlayer(player, new RtsPlacedBuildingSyncPacket(entries, chunkList));
    }

    public static int rotationFromFacing(String facing) {
        if (facing == null) {
            return 0;
        }
        return switch (facing.toLowerCase()) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }
}
