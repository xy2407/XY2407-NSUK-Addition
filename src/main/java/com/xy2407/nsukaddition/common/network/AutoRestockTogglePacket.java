package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 自动补货开关网络包，客户端向服务端发送切换自动补货状态的请求。 */
@SuppressWarnings("null")
public record AutoRestockTogglePacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {

    public static final Type<AutoRestockTogglePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "auto_restock_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutoRestockTogglePacket> STREAM_CODEC =
            StreamCodec.of(AutoRestockTogglePacket::encode, AutoRestockTogglePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, AutoRestockTogglePacket p) {
        buf.writeBlockPos(p.pos());
        buf.writeBoolean(p.enabled());
    }

    public static AutoRestockTogglePacket decode(RegistryFriendlyByteBuf buf) {
        return new AutoRestockTogglePacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(AutoRestockTogglePacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            AutoRestockConfig.setEnabled(level, p.pos(), p.enabled());
        }
    }
}
