package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** 玩家请求自己的外交关系数据，服务端加载后回送客户端。 */
public record DiplomacyDataRequestPacket() implements CustomPacketPayload {

    public static final Type<DiplomacyDataRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "diplomacy_data_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiplomacyDataRequestPacket> STREAM_CODEC =
            StreamCodec.of(DiplomacyDataRequestPacket::encode, DiplomacyDataRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, DiplomacyDataRequestPacket p) {
    }

    public static DiplomacyDataRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new DiplomacyDataRequestPacket();
    }

    public static void handle(DiplomacyDataRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        List<DiplomacyRelation> relations = DiplomacyStorage.loadRelations(level, player.getUUID());
        PacketDistributor.sendToPlayer(player, new DiplomacyDataPacket(relations));
    }
}
