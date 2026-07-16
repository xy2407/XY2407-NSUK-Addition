package com.xy2407.nsukaddition.common.network.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.OreVeinDiscoveryBridge;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** 矿脉发现响应包，由服务端发送矿脉位置与类型信息至客户端。 */
public record OreVeinDiscoveryResponsePacket(Map<Long, OreVeinType> veins, String oreTypeName) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "ore_vein_discovery_response");
    public static final Type<OreVeinDiscoveryResponsePacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OreVeinDiscoveryResponsePacket> STREAM_CODEC =
            StreamCodec.of(OreVeinDiscoveryResponsePacket::encode, OreVeinDiscoveryResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, OreVeinDiscoveryResponsePacket p) {
        buf.writeUtf(p.oreTypeName == null ? "" : p.oreTypeName);
        buf.writeInt(p.veins == null ? 0 : p.veins.size());
        if (p.veins != null) {
            for (Map.Entry<Long, OreVeinType> e : p.veins.entrySet()) {
                buf.writeLong(e.getKey());
                buf.writeEnum(e.getValue());
            }
        }
    }

    public static OreVeinDiscoveryResponsePacket decode(RegistryFriendlyByteBuf buf) {
        String oreTypeName = buf.readUtf();
        int size = buf.readInt();
        Map<Long, OreVeinType> veins = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            long pos = buf.readLong();
            OreVeinType type = buf.readEnum(OreVeinType.class);
            veins.put(pos, type);
        }
        return new OreVeinDiscoveryResponsePacket(veins, oreTypeName);
    }

    public static void handle(OreVeinDiscoveryResponsePacket packet, IPayloadContext context) {
        OreVeinDiscoveryBridge.handle(packet, context);
    }
}
