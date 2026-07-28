package com.xy2407.nsukaddition.common.network.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityCoreMoveService;
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

/** 城市核心迁移确认网络包，客户端选定新位置后发送到服务端执行迁移。 */
@SuppressWarnings("null")
public record CityCoreMovePacket(BlockPos oldCorePos, BlockPos newCorePos, UUID cityId) implements CustomPacketPayload {

    public static final Type<CityCoreMovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "city_core_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreMovePacket> STREAM_CODEC =
            StreamCodec.of(CityCoreMovePacket::encode, CityCoreMovePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, CityCoreMovePacket p) {
        b.writeBlockPos(p.oldCorePos());
        b.writeBlockPos(p.newCorePos());
        b.writeUUID(p.cityId());
    }

    public static CityCoreMovePacket decode(RegistryFriendlyByteBuf b) {
        return new CityCoreMovePacket(b.readBlockPos(), b.readBlockPos(), b.readUUID());
    }

    public static void handle(CityCoreMovePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        CityCoreMoveService.MoveResult result = CityCoreMoveService.executeMove(level, player, p.cityId(), p.oldCorePos(), p.newCorePos());
        switch (result) {
            case SUCCESS ->
                InfoToastService.success(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.success"));
            case FAIL_NO_PERMISSION ->
                InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.no_permission"));
            case FAIL_CITY_NOT_FOUND ->
                InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.city_not_found"));
            case FAIL_POS_MISMATCH ->
                InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.pos_mismatch"));
            case FAIL_SAME_POSITION ->
                InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.same_position"));
            case FAIL_NEW_POS_OCCUPIED ->
                InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.new_pos_occupied"));
            case FAIL_CHUNK_NOT_OWNED ->
                InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.city_core_move.chunk_not_owned"));
        }
    }
}
