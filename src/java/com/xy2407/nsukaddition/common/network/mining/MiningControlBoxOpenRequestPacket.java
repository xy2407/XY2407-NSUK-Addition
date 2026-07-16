package com.xy2407.nsukaddition.common.network.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 挖矿控制盒打开请求网络包，客户端请求获取控制盒视图数据。 */
@SuppressWarnings("null")
public record MiningControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<MiningControlBoxOpenRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "mining_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(MiningControlBoxOpenRequestPacket::encode, MiningControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, MiningControlBoxOpenRequestPacket p) { b.writeBlockPos(p.pos()); }
    public static MiningControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf b) { return new MiningControlBoxOpenRequestPacket(b.readBlockPos()); }

    public static void handle(MiningControlBoxOpenRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            PacketDistributor.sendToPlayer(player,
                    MiningControlBoxOpenResponsePacket.from(MiningControlBoxService.buildView(level, p.pos())));
        }
    }
}
