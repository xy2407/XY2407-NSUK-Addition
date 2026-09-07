package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import com.xy2407.nsukaddition.server.city.DialogNpcDialogService;
import com.xy2407.nsukaddition.server.city.VillageTourismService;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.commercial.CommercialTradeView;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 请求重新推送当前商队/商店交易视图：客户端在每次交易后发送，服务端用最新库存构建
 * CommercialTradeOpenResponsePacket 回推，触发 CommercialTradeUiRoot.refreshActive 刷新增量。
 */
public record CommercialTradeRefreshRequestPacket(BlockPos pos, UUID workerId) implements CustomPacketPayload {

    public static final Type<CommercialTradeRefreshRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "commercial_trade_refresh"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialTradeRefreshRequestPacket> STREAM_CODEC =
            StreamCodec.of(CommercialTradeRefreshRequestPacket::encode, CommercialTradeRefreshRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CommercialTradeRefreshRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUUID(packet.workerId());
    }

    public static CommercialTradeRefreshRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CommercialTradeRefreshRequestPacket(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(CommercialTradeRefreshRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        try {
            if (level.getEntity(packet.workerId()) instanceof DialogNpcEntity) {
                CommercialTradeView shopView = DialogNpcDialogService.buildInitialShopView(level, packet.workerId());
                if (shopView != null) {
                    PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(shopView));
                }
                return;
            }
            if (VillageTourismService.isCaravanLeader(level, packet.workerId())) {
                CommercialTradeView caravanView = VillageTourismService.buildCaravanTradeView(level, packet.workerId());
                if (caravanView != null) {
                    PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(caravanView));
                }
                return;
            }
            CommercialTradeOpenResponsePacket view = CommercialTradeOpenResponsePacket.from(
                    CommercialControlBoxService.buildTradeView(level, packet.pos(), packet.workerId()));
            PacketDistributor.sendToPlayer(player, view);
        } catch (RuntimeException e) {
            NsukAddition.LOGGER.error("Failed to rebuild commercial trade view for refresh", e);
        }
    }
}