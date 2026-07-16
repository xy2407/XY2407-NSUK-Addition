package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 市民搬迁网络包，将指定市民从原领地搬迁到附属地，解雇原有工作并清除住宅信息。 */
@SuppressWarnings("null")
public record ColonyCitizenRelocatePacket(UUID citizenId, UUID targetColonyId) implements CustomPacketPayload {

    public static final Type<ColonyCitizenRelocatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_citizen_relocate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyCitizenRelocatePacket> STREAM_CODEC =
            StreamCodec.of(ColonyCitizenRelocatePacket::encode, ColonyCitizenRelocatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyCitizenRelocatePacket p) {
        b.writeUUID(p.citizenId());
        b.writeUUID(p.targetColonyId());
    }

    public static ColonyCitizenRelocatePacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyCitizenRelocatePacket(b.readUUID(), b.readUUID());
    }

    public static void handle(ColonyCitizenRelocatePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.targetColonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_RELOCATE_COLONY_NOT_FOUND));
            return;
        }

        CitizenEntity citizenEntity = CitizenTeleportService.findCitizenEntity(level, p.citizenId());
        if (citizenEntity == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_RELOCATE_CITIZEN_NOT_FOUND));
            return;
        }

        UUID currentColony = ColonySqliteStorage.getColonyForCitizen(level, p.citizenId());
        if (p.targetColonyId.equals(currentColony)) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_RELOCATE_ALREADY_HERE));
            return;
        }

        CitizenService.setWorkplace(level, p.citizenId(), null);
        CitizenData citizenData = CitizenService.findCitizen(level, p.citizenId()).orElse(null);
        if (citizenData != null) {
            citizenData.setJobType(CityJobType.UNEMPLOYED);
        }

        CitizenService.setHome(level, p.citizenId(), null);

        if (currentColony != null) {
            ColonySqliteStorage.removeCitizen(level, p.citizenId());
        }

        ColonySqliteStorage.assignCitizen(level, p.citizenId(), p.targetColonyId());

        BlockPos corePos = colony.corePos();
        CitizenTeleportService.teleportCitizen(level, p.citizenId(),
                new Vec3(corePos.getX() + 0.5, corePos.getY(), corePos.getZ() + 0.5));

        InfoToastService.success(player, Component.translatable(
                ColonyConstants.MSG_RELOCATE_SUCCESS, citizenEntity.getCitizenName(), colony.name()));
    }
}
