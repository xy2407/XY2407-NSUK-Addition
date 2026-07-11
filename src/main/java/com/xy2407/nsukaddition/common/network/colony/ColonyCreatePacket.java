package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyCreateService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.city.CityData;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 附属地创建网络包，客户端发送附属地名称，服务端校验后创建附属地。 */
@SuppressWarnings("null")
public record ColonyCreatePacket(BlockPos pos, String colonyName) implements CustomPacketPayload {

    public static final Type<ColonyCreatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_create"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyCreatePacket> STREAM_CODEC =
            StreamCodec.of(ColonyCreatePacket::encode, ColonyCreatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyCreatePacket p) {
        b.writeBlockPos(p.pos());
        b.writeUtf(p.colonyName(), 64);
    }

    public static ColonyCreatePacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyCreatePacket(b.readBlockPos(), b.readUtf(64));
    }

    public static void handle(ColonyCreatePacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            createColony(level, player, p.pos(), p.colonyName());
        }
    }

    private static void createColony(ServerLevel level, ServerPlayer player, BlockPos pos, String rawName) {
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_TOO_FAR));
            return;
        }
        if (ColonyCreateService.hasColonyAt(level, pos)) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.colony.already_bound"));
            ColonyCoreOpenRequestPacket.openFor(level, player, pos);
            return;
        }
        CityData city = CityService.findManagedPlayerCity(level, player.getUUID()).orElse(null);
        if (city == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_NO_CITY));
            ColonyCoreOpenRequestPacket.openFor(level, player, pos);
            return;
        }
        String name = normalizeName(rawName);
        if (!isValidName(name)) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.colony.invalid_name"));
            ColonyCoreOpenRequestPacket.openFor(level, player, pos);
            return;
        }
        ColonyCreateService.CreateResult result = ColonyCreateService.createColony(level, player, pos, name);
        if (result != ColonyCreateService.CreateResult.SUCCESS) {
            var msg = switch (result) {
                case FAIL_NO_CITY -> Component.translatable(ColonyConstants.MSG_NO_CITY);
                case FAIL_LEVEL_TOO_LOW -> Component.translatable(ColonyConstants.MSG_LEVEL_TOO_LOW);
                case FAIL_COLONY_LIMIT -> Component.translatable(ColonyConstants.MSG_COLONY_LIMIT);
                case FAIL_CHUNK_POOL_EMPTY -> Component.translatable(ColonyConstants.MSG_CHUNK_POOL_EMPTY);
                case FAIL_POS_ALREADY_CLAIMED -> Component.translatable(ColonyConstants.MSG_POS_ALREADY_CLAIMED);
                default -> Component.translatable(ColonyConstants.MSG_CREATE_FAILED);
            };
            InfoToastService.warning(player, msg);
            ColonyCoreOpenRequestPacket.openFor(level, player, pos);
            return;
        }
        InfoToastService.success(player, Component.translatable(ColonyConstants.MSG_CREATE_SUCCESS, name));
        ColonyCoreOpenRequestPacket.openFor(level, player, pos);
    }

    private static String normalizeName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static boolean isValidName(String name) {
        return name.length() >= 2 && name.length() <= 20;
    }
}
