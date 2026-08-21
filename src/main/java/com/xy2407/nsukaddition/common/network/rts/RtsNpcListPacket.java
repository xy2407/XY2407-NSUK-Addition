package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.RtsNpcListBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** RTS 专属 NPC 列表同步(server→client)：玩家所属城市的全部市民 UUID+职业，RTS 归属判断专用，与 HUD 快照隔离。 */
public record RtsNpcListPacket(UUID cityId, List<NpcEntry> npcs) implements CustomPacketPayload {

    public record NpcEntry(UUID uuid, String jobType) {
    }

    public static final Type<RtsNpcListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_npc_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsNpcListPacket> STREAM_CODEC =
            StreamCodec.of(RtsNpcListPacket::encode, RtsNpcListPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsNpcListPacket p) {
        b.writeBoolean(p.cityId() != null);
        if (p.cityId() != null) {
            b.writeUUID(p.cityId());
        }
        b.writeInt(p.npcs().size());
        for (NpcEntry e : p.npcs()) {
            b.writeUUID(e.uuid());
            b.writeUtf(e.jobType() != null ? e.jobType() : "", 32);
        }
    }

    public static RtsNpcListPacket decode(RegistryFriendlyByteBuf b) {
        UUID cityId = b.readBoolean() ? b.readUUID() : null;
        int size = b.readInt();
        List<NpcEntry> npcs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            npcs.add(new NpcEntry(b.readUUID(), b.readUtf(32)));
        }
        return new RtsNpcListPacket(cityId, npcs);
    }

    public static void handle(RtsNpcListPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RtsNpcListBridge.handle(p));
    }
}
