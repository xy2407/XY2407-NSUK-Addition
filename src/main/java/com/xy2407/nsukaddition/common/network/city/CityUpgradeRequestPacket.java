package com.xy2407.nsukaddition.common.network.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityUpgradeService;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 城市升级请求网络包，客户端向服务端发送城市升级请求。 */
@SuppressWarnings("null")
public record CityUpgradeRequestPacket(BlockPos corePos, UUID cityId) implements CustomPacketPayload {

    public static final Type<CityUpgradeRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "city_upgrade_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityUpgradeRequestPacket> STREAM_CODEC =
            StreamCodec.of(CityUpgradeRequestPacket::encode, CityUpgradeRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, CityUpgradeRequestPacket p) {
        b.writeBlockPos(p.corePos());
        b.writeUUID(p.cityId());
    }

    public static CityUpgradeRequestPacket decode(RegistryFriendlyByteBuf b) {
        return new CityUpgradeRequestPacket(b.readBlockPos(), b.readUUID());
    }

    public static void handle(CityUpgradeRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        CityUpgradeService.UpgradeResult result = CityUpgradeService.executeUpgrade(level, p.cityId(), player);
        switch (result) {
            case SUCCESS -> {
                InfoToastService.success(player, net.minecraft.network.chat.Component.translatable("message.xy2407_nsuk_addition.city_upgrade.success"));

                CityCoreOpenRequestPacket.openFor(level, player, p.corePos());
            }
            case FAIL_ALREADY_MAX -> InfoToastService.warning(player,
                    net.minecraft.network.chat.Component.translatable("message.xy2407_nsuk_addition.city_upgrade.already_max"));
            case FAIL_CONDITION_NOT_MET -> InfoToastService.warning(player,
                    net.minecraft.network.chat.Component.translatable("message.xy2407_nsuk_addition.city_upgrade.condition_not_met"));
            case FAIL_MATERIAL_SHORTAGE -> InfoToastService.warning(player,
                    net.minecraft.network.chat.Component.translatable("message.xy2407_nsuk_addition.city_upgrade.material_shortage"));
            case FAIL_FUNDS_SHORTAGE -> InfoToastService.warning(player,
                    net.minecraft.network.chat.Component.translatable("message.xy2407_nsuk_addition.city_upgrade.funds_shortage"));
            default -> InfoToastService.warning(player,
                    net.minecraft.network.chat.Component.translatable("message.xy2407_nsuk_addition.city_upgrade.unknown"));
        }
    }
}
