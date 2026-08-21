package com.xy2407.nsukaddition.common.network.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/** 玩家请求与指定村庄城市建立外交关系，服务端校验村庄类型后建交并回送最新数据。 */
public record EstablishDiplomacyRequestPacket(UUID cityId, int posX, int posZ) implements CustomPacketPayload {

    public static final Type<EstablishDiplomacyRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "establish_diplomacy_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EstablishDiplomacyRequestPacket> STREAM_CODEC =
            StreamCodec.of(EstablishDiplomacyRequestPacket::encode, EstablishDiplomacyRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, EstablishDiplomacyRequestPacket p) {
        buf.writeUUID(p.cityId());
        buf.writeInt(p.posX());
        buf.writeInt(p.posZ());
    }

    public static EstablishDiplomacyRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new EstablishDiplomacyRequestPacket(
                buf.readUUID(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(EstablishDiplomacyRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!DiplomacyStorage.hasRelation(level, player.getUUID(), p.posX(), p.posZ())) {
            String villageType = VillageCityTypeStorage.getVillageType(level, p.cityId());
            if (villageType != null) {
                String cityName = CityService.findCity(level, p.cityId()).map(CityData::cityName).orElse("");
                DiplomacyStorage.establishRelation(level, player.getUUID(), villageType, p.posX(), p.posZ(), p.cityId().toString(), cityName);
            }
        }
        List<DiplomacyRelation> relations = DiplomacyStorage.loadRelations(level, player.getUUID());
        PacketDistributor.sendToPlayer(player, new DiplomacyDataPacket(relations));
    }
}
