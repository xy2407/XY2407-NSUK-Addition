package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 外贸控制箱操作网络包，客户端发送启动/停止/雇佣/解雇等操作请求。 */
@SuppressWarnings("null")
public record ForeignTradeControlBoxActionPacket(BlockPos boxPos, Action action) implements CustomPacketPayload {

    public static final Type<ForeignTradeControlBoxActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeControlBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeControlBoxActionPacket::encode, ForeignTradeControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeControlBoxActionPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeEnum(p.action());
    }

    public static ForeignTradeControlBoxActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeControlBoxActionPacket(buf.readBlockPos(), buf.readEnum(Action.class));
    }

    public static void handle(ForeignTradeControlBoxActionPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.boxPos(), 16.0D)) return;
            if (!level.getBlockState(p.boxPos()).is(ModBlocks.FOREIGN_TRADE_CONTROL_BOX.get())) return;
            switch (p.action()) {
                case TOGGLE_RUN -> ForeignTradeControlBoxService.toggleRunning(level, p.boxPos());
                case FIRE -> ForeignTradeControlBoxService.fireWorker(level, p.boxPos());
                default -> {}
            }
            PacketDistributor.sendToPlayer(player,
                    ForeignTradeControlBoxOpenResponsePacket.from(ForeignTradeControlBoxService.buildView(level, p.boxPos())));
        }
    }

    public enum Action {
        TOGGLE_RUN,
        HIRE,
        FIRE
    }
}
