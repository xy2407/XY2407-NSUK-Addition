package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.rts.RtsBuildingMoveService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 客户端请求迁移建筑:清除原位置建筑(方块+库数据)并在新位置完整重建,同步住客/工人。
 */
public record RtsBuildingMovePacket(UUID buildingId, BlockPos newOrigin, int rotation) implements CustomPacketPayload {

    public static final Type<RtsBuildingMovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_building_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsBuildingMovePacket> STREAM_CODEC =
            StreamCodec.of(RtsBuildingMovePacket::encode, RtsBuildingMovePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RtsBuildingMovePacket p) {
        b.writeUUID(p.buildingId());
        b.writeBlockPos(p.newOrigin());
        b.writeVarInt(p.rotation());
    }

    public static RtsBuildingMovePacket decode(RegistryFriendlyByteBuf b) {
        return new RtsBuildingMovePacket(b.readUUID(), b.readBlockPos(), b.readVarInt());
    }

    public static void handle(RtsBuildingMovePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ctx.enqueueWork(() -> {
            UUID cityId = null;
            for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
                if (rec != null && p.buildingId().equals(rec.buildingId())) {
                    cityId = rec.cityId();
                    break;
                }
            }
            boolean ok = RtsBuildingMoveService.moveBuilding(level, player, p.buildingId(), p.newOrigin(), p.rotation());
            if (ok) {
                InfoToastService.success(player, Component.translatable(
                        "message.xy2407_nsuk_addition.rts.building_moved"));
                if (cityId != null) {
                    PacketDistributor.sendToPlayer(player, new RtsBuildingBoundsClearPacket());
                    RtsSelectionSyncPacket.sendBuildingBoundsForCity(level, player, cityId);
                    RtsPlacedBuildingSyncRequestPacket.sendCityBuildingSync(level, player, cityId);
                }
            } else {
                InfoToastService.warning(player, Component.translatable(
                        "message.xy2407_nsuk_addition.rts.building_move_failed"));
            }
        });
    }
}
