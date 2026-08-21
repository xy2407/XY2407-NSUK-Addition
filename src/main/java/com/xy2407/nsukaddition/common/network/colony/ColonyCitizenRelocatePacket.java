package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
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

/** 市民搬迁网络包，将指定市民从其它领地/主城搬迁到目标附属地：权限校验 + 彻底解雇(雇佣隔离) + 清除住宅 + 传送。 */
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

        var cityOpt = CityService.findCity(level, colony.parentCityId());
        if (cityOpt.isEmpty() || !cityOpt.get().hasPermission(player.getUUID(), CityPermissionLevel.MAYOR)) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.colony.no_permission"));
            return;
        }

        CitizenEntity citizenEntity = CitizenTeleportService.findCitizenEntity(level, p.citizenId());
        if (citizenEntity == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_RELOCATE_CITIZEN_NOT_FOUND));
            return;
        }

        UUID currentColony = ColonySqliteStorage.getColonyForCitizen(level, p.citizenId());
        if (p.targetColonyId().equals(currentColony)) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_RELOCATE_ALREADY_HERE));
            return;
        }

        CitizenService.clearEmployment(level, p.citizenId());
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
