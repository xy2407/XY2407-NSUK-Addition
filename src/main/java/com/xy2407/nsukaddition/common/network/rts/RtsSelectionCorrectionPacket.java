package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.RtsSelectionCorrectionBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 服务端回传过滤后的 RTS 选中集合，纠正客户端本地选中(非本城 NPC 一律不可选中/不残留)。 */
public record RtsSelectionCorrectionPacket(Set<UUID> selectedIds) implements CustomPacketPayload {
    public static final Type<RtsSelectionCorrectionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_selection_correction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsSelectionCorrectionPacket> STREAM_CODEC =
            StreamCodec.of(RtsSelectionCorrectionPacket::encode, RtsSelectionCorrectionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsSelectionCorrectionPacket p) {
        b.writeInt(p.selectedIds().size());
        for (UUID id : p.selectedIds()) {
            b.writeUUID(id);
        }
    }

    public static RtsSelectionCorrectionPacket decode(RegistryFriendlyByteBuf b) {
        int size = b.readInt();
        Set<UUID> ids = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(b.readUUID());
        }
        return new RtsSelectionCorrectionPacket(ids);
    }

    public static void handle(RtsSelectionCorrectionPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RtsSelectionCorrectionBridge.handle(p.selectedIds()));
    }
}
