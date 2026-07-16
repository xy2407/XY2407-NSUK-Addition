package com.xy2407.nsukaddition.common.network.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
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

/** 繁育控制箱打开请求网络包，客户端请求服务端返回繁育控制箱界面数据。 */
@SuppressWarnings("null")
public record BreedingControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<BreedingControlBoxOpenRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "breeding_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreedingControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(BreedingControlBoxOpenRequestPacket::encode, BreedingControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, BreedingControlBoxOpenRequestPacket p) {
        b.writeBlockPos(p.pos());
    }

    public static BreedingControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf b) {
        return new BreedingControlBoxOpenRequestPacket(b.readBlockPos());
    }

    public static void handle(BreedingControlBoxOpenRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, p.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable(BreedingConstants.TOO_FAR_MESSAGE));
            return;
        }
        PacketDistributor.sendToPlayer(player, BreedingControlBoxOpenResponsePacket.from(BreedingControlBoxService.buildView(level, pos)));
    }
}
