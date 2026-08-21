package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.common.network.clientbound.ContainerRoleBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 容器角色响应包，携带该位置的所有角色（如 input 和 output 同箱则包含两条）。 */
public record ContainerRoleResponsePacket(BlockPos containerPos, List<RoleEntry> roles) implements CustomPacketPayload {

    public static final Type<ContainerRoleResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "container_role_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerRoleResponsePacket> STREAM_CODEC =
            StreamCodec.of(ContainerRoleResponsePacket::encode, ContainerRoleResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(RegistryFriendlyByteBuf buf, ContainerRoleResponsePacket p) {
        buf.writeBlockPos(p.containerPos);
        buf.writeVarInt(p.roles.size());
        for (RoleEntry e : p.roles) {
            buf.writeUtf(e.role);
            buf.writeUtf(e.boxType);
            buf.writeVarInt(e.relativeX);
            buf.writeVarInt(e.relativeY);
            buf.writeVarInt(e.relativeZ);
        }
    }

    private static ContainerRoleResponsePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<RoleEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new RoleEntry(buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new ContainerRoleResponsePacket(pos, list);
    }

    public static void handle(ContainerRoleResponsePacket p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> ContainerRoleBridge.handle(p));
    }

    public record RoleEntry(String role, String boxType, int relativeX, int relativeY, int relativeZ) {}
}
