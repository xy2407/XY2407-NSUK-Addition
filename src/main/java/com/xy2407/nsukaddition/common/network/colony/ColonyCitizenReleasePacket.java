package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
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

/**
 * 附属地人口面板的迁移按钮：把当前附属地的市民释放回主城市。
 * 逻辑：清空工作/住宅/职业(保证雇佣与居住的城市隔离) + 解除附属地分配(sqlite) + 传送到主城市核心。
 */
@SuppressWarnings("null")
public record ColonyCitizenReleasePacket(UUID citizenId) implements CustomPacketPayload {

    public static final Type<ColonyCitizenReleasePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_citizen_release"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyCitizenReleasePacket> STREAM_CODEC =
            StreamCodec.of(ColonyCitizenReleasePacket::encode, ColonyCitizenReleasePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, ColonyCitizenReleasePacket p) {
        b.writeUUID(p.citizenId());
    }

    public static ColonyCitizenReleasePacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyCitizenReleasePacket(b.readUUID());
    }

    public static void handle(ColonyCitizenReleasePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        CitizenEntity citizenEntity = CitizenTeleportService.findCitizenEntity(level, p.citizenId());
        if (citizenEntity == null) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.relocate_citizen_not_found"));
            return;
        }
        CitizenData data = CitizenService.findCitizen(level, p.citizenId()).orElse(null);
        if (data == null || data.cityId() == null) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.relocate_citizen_not_found"));
            return;
        }
        UUID currentColony = ColonySqliteStorage.getColonyForCitizen(level, p.citizenId());
        if (currentColony == null) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.release_not_in_colony"));
            return;
        }

        CityData city = CityManager.get(level).getCity(data.cityId()).orElse(null);
        if (city == null || !city.hasPermission(player.getUUID(), CityPermissionLevel.MAYOR)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.no_permission"));
            return;
        }

        CitizenService.clearEmployment(level, p.citizenId());
        CitizenService.setHome(level, p.citizenId(), null);

        ColonySqliteStorage.removeCitizen(level, p.citizenId());

        BlockPos core = city.cityCorePos();
        CitizenTeleportService.teleportCitizen(level, p.citizenId(),
                new Vec3(core.getX() + 0.5, core.getY(), core.getZ() + 0.5));

        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.release_success",
                citizenEntity.getCitizenName(),
                city.cityName()));
    }
}
