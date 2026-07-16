package com.xy2407.nsukaddition.common.network.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 矿脉区块同步包，由服务端批量发送区块矿脉数据至客户端进行缓存同步。 */
public record OreVeinChunkSyncPacket(
        List<Entry> entries
) implements CustomPacketPayload {

    public static final Type<OreVeinChunkSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "ore_vein_chunk_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OreVeinChunkSyncPacket> STREAM_CODEC =
            StreamCodec.of(OreVeinChunkSyncPacket::encode, OreVeinChunkSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(long chunkPos, OreVeinType oreType) {}

    public static void encode(RegistryFriendlyByteBuf b, OreVeinChunkSyncPacket p) {
        b.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            b.writeLong(e.chunkPos);
            b.writeByte(e.oreType.ordinal());
        }
    }

    public static OreVeinChunkSyncPacket decode(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count == 0) return new OreVeinChunkSyncPacket(Collections.emptyList());
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long chunkPos = b.readLong();
            int ordinal = b.readByte();
            OreVeinType type = ordinal >= 0 && ordinal < OreVeinType.values().length
                    ? OreVeinType.values()[ordinal]
                    : OreVeinType.COAL;
            list.add(new Entry(chunkPos, type));
        }
        return new OreVeinChunkSyncPacket(list);
    }

    public static void handle(OreVeinChunkSyncPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.xy2407.nsukaddition.client.network.vein.OreVeinSyncClientHandler.handle(p));
    }
}
