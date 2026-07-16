package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyCreateService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import com.xy2407.nsukaddition.server.city.TownImmigrationService;
import com.xy2407.nsukaddition.server.city.VillageTourismService;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 附属地删除网络包，客户端发送确认名称，服务端校验后销毁附属地。 */
@SuppressWarnings("null")
public record ColonyDeletePacket(UUID colonyId, String confirmName) implements CustomPacketPayload {

    public static final Type<ColonyDeletePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_delete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyDeletePacket> STREAM_CODEC =
            StreamCodec.of(ColonyDeletePacket::encode, ColonyDeletePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyDeletePacket p) {
        b.writeUUID(p.colonyId());
        b.writeUtf(p.confirmName(), 64);
    }

    public static ColonyDeletePacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyDeletePacket(b.readUUID(), b.readUtf(64));
    }

    public static void handle(ColonyDeletePacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            deleteColony(level, player, p.colonyId(), p.confirmName());
        }
    }

    private static void deleteColony(ServerLevel level, ServerPlayer player, UUID colonyId, String confirmName) {
        ColonyData colony = ColonySqliteStorage.loadColonyById(level, colonyId);
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.colony.not_found"));
            return;
        }

        var cityOpt = CityService.findCity(level, colony.parentCityId());
        if (cityOpt.isEmpty()
                || !cityOpt.get().hasPermission(player.getUUID(), CityPermissionLevel.MAYOR)) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_NO_CITY));
            return;
        }

        if (!colony.name().equals(confirmName)) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.delete_confirm_failed"));
            ColonyCoreOpenRequestPacket.openFor(level, player, colony.corePos());
            return;
        }

        BlockPos corePos = colony.corePos();
        VillageTourismService.onCityDeleted(colonyId);
        TownImmigrationService.onCityDeleted(level, colonyId);
        ColonyCreateService.onCoreRemoved(level, corePos);

        InfoToastService.success(player, Component.translatable(
                ColonyConstants.MSG_DESTROYED, colony.name()));

        ColonyCoreOpenRequestPacket.openFor(level, player, corePos);
    }
}
