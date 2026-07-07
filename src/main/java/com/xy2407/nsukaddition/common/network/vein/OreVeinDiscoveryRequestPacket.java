package com.xy2407.nsukaddition.common.network.vein;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 矿脉发现请求包，由客户端发送指定区块坐标以查询矿脉信息。 */
public record OreVeinDiscoveryRequestPacket(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "ore_vein_discovery_request");
    public static final Type<OreVeinDiscoveryRequestPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OreVeinDiscoveryRequestPacket> STREAM_CODEC =
            StreamCodec.of(OreVeinDiscoveryRequestPacket::encode, OreVeinDiscoveryRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, OreVeinDiscoveryRequestPacket p) {
        buf.writeInt(p.chunkX);
        buf.writeInt(p.chunkZ);
    }

    public static OreVeinDiscoveryRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new OreVeinDiscoveryRequestPacket(buf.readInt(), buf.readInt());
    }
}
