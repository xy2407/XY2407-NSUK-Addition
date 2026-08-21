package com.xy2407.nsukaddition.common.network.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityCorePositionsCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端同步当前维度所有城市核心位置及归属信息到客户端，用于发光轮廓渲染。 */
public record CityCorePositionsPacket(List<CoreInfo> cores) implements CustomPacketPayload {

    public static final Type<CityCorePositionsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "city_core_positions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCorePositionsPacket> STREAM_CODEC =
            StreamCodec.of(CityCorePositionsPacket::encode, CityCorePositionsPacket::decode);

    public record CoreInfo(BlockPos pos, boolean mine) {}

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, CityCorePositionsPacket p) {
        buf.writeVarInt(p.cores().size());
        for (CoreInfo info : p.cores()) {
            buf.writeBlockPos(info.pos());
            buf.writeBoolean(info.mine());
        }
    }

    public static CityCorePositionsPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<CoreInfo> cores = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cores.add(new CoreInfo(buf.readBlockPos(), buf.readBoolean()));
        }
        return new CityCorePositionsPacket(cores);
    }

    public static void handle(CityCorePositionsPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CityCorePositionsCache.update(p.cores()));
    }
}
