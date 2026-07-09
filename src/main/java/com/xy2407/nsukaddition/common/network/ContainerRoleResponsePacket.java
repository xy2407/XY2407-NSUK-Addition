package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.container.ContainerRoleClientCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ContainerRoleResponsePacket(BlockPos containerPos, String role, String boxType,
                                          int relativeX, int relativeY, int relativeZ) implements CustomPacketPayload {

    public static final Type<ContainerRoleResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "container_role_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerRoleResponsePacket> STREAM_CODEC =
            StreamCodec.of(ContainerRoleResponsePacket::encode, ContainerRoleResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(RegistryFriendlyByteBuf buf, ContainerRoleResponsePacket p) {
        buf.writeBlockPos(p.containerPos);
        buf.writeUtf(p.role);
        buf.writeUtf(p.boxType);
        buf.writeVarInt(p.relativeX);
        buf.writeVarInt(p.relativeY);
        buf.writeVarInt(p.relativeZ);
    }

    private static ContainerRoleResponsePacket decode(RegistryFriendlyByteBuf buf) {
        return new ContainerRoleResponsePacket(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(ContainerRoleResponsePacket p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> ContainerRoleClientCache.setResponse(p));
    }

    /** 容器角色记录：标识容器是 input 还是 output，及所属建筑类型和相对坐标 */
    public record ContainerRole(String role, String boxType, int relativeX, int relativeY, int relativeZ) {}
}
