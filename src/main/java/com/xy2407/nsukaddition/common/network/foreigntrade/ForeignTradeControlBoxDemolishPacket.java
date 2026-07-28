package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 外贸控制箱拆除网络包，客户端发送拆除请求。 */
@SuppressWarnings("null")
public record ForeignTradeControlBoxDemolishPacket(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<ForeignTradeControlBoxDemolishPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "foreign_trade_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForeignTradeControlBoxDemolishPacket> STREAM_CODEC =
            StreamCodec.of(ForeignTradeControlBoxDemolishPacket::encode, ForeignTradeControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, ForeignTradeControlBoxDemolishPacket p) {
        buf.writeBlockPos(p.boxPos());
    }

    public static ForeignTradeControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buf) {
        return new ForeignTradeControlBoxDemolishPacket(buf.readBlockPos());
    }

    public static void handle(ForeignTradeControlBoxDemolishPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.boxPos(), 16.0D)) return;
            if (!level.getBlockState(p.boxPos()).is(ModBlocks.FOREIGN_TRADE_CONTROL_BOX.get())) return;
            java.util.UUID cityId = CityChunkManager.get(level).getChunkOwner(new ChunkPos(p.boxPos()).toLong());
            if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) return;
            ForeignTradeControlBoxService.onRemoved(level, p.boxPos());
            level.removeBlock(p.boxPos(), false);
        }
    }
}
