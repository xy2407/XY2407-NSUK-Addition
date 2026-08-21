package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.compat.maid.MaidWaiterBridge;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 餐厅女仆雇佣候选列表响应网络包，服务端返回玩家已驯服且可雇佣的女仆列表。
 */
@SuppressWarnings("null")
public record RestaurantMaidHireResponsePacket(BlockPos pos,
                                               List<MaidCandidate> candidates) implements CustomPacketPayload {

    public static final Type<RestaurantMaidHireResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_maid_hire_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantMaidHireResponsePacket> STREAM_CODEC =
            StreamCodec.of(RestaurantMaidHireResponsePacket::encode, RestaurantMaidHireResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, RestaurantMaidHireResponsePacket p) {
        buf.writeBlockPos(p.pos());
        buf.writeVarInt(p.candidates().size());
        for (MaidCandidate c : p.candidates()) {
            buf.writeUUID(c.uuid());
            buf.writeUtf(c.name(), 64);
            buf.writeBoolean(c.hired());
            buf.writeUtf(c.type(), 16);
        }
    }

    public static RestaurantMaidHireResponsePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<MaidCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            candidates.add(new MaidCandidate(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(16)));
        }
        return new RestaurantMaidHireResponsePacket(pos, List.copyOf(candidates));
    }

    public static void sendTo(ServerLevel level, ServerPlayer player, BlockPos pos) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.getOrCreate(pos);
        List<MaidCandidate> candidates = new ArrayList<>();
        if (MaidWaiterBridge.isLoaded()) {
            for (LivingEntity maid : MaidWaiterBridge.findTamedMaids(level, player)) {
                if (maid == null) continue;
                UUID uuid = maid.getUUID();
                boolean hiredElsewhere = false;
                for (RestaurantBoxData other : manager.all()) {
                    if (other.boxPos().equals(pos)) continue;
                    if (other.hasMaid(uuid)) { hiredElsewhere = true; break; }
                }
                if (hiredElsewhere) continue;
                candidates.add(new MaidCandidate(uuid, MaidWaiterBridge.displayName(maid), data.hasMaid(uuid), "maid"));
            }
        }
        PacketDistributor.sendToPlayer(player, new RestaurantMaidHireResponsePacket(pos, List.copyOf(candidates)));
    }

    public static void handle(RestaurantMaidHireResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.xy2407.nsukaddition.common.network.clientbound.RestaurantMaidHireBridge.open(p));
    }

    public record MaidCandidate(UUID uuid, String name, boolean hired, String type) {
    }
}
