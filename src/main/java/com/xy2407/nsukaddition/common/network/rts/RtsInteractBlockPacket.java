package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 客户端→服务端：RTS 模式下选中假人右键可交互方块，假人靠近 5 格内后用玩家身份远程触发方块交互。 */
public record RtsInteractBlockPacket(UUID fakePlayerId, BlockPos blockPos, Direction direction, Vec3 location) implements CustomPacketPayload {

    private static final double INTERACT_RANGE = 5.0D;

    public static final Type<RtsInteractBlockPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_interact_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsInteractBlockPacket> STREAM_CODEC =
            StreamCodec.of(RtsInteractBlockPacket::encode, RtsInteractBlockPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsInteractBlockPacket p) {
        b.writeUUID(p.fakePlayerId());
        b.writeBlockPos(p.blockPos());
        b.writeEnum(p.direction());
        b.writeVec3(p.location());
    }

    public static RtsInteractBlockPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsInteractBlockPacket(b.readUUID(), b.readBlockPos(), b.readEnum(Direction.class), b.readVec3());
    }

    public static void handle(RtsInteractBlockPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (p.fakePlayerId() == null || p.blockPos() == null || p.direction() == null) return;

        ctx.enqueueWork(() -> {
            Entity entity = level.getEntity(p.fakePlayerId());
            if (entity == null) {
                for (ServerLevel sl : player.getServer().getAllLevels()) {
                    entity = sl.getEntity(p.fakePlayerId());
                    if (entity != null) break;
                }
            }
            if (!(entity instanceof RtsFakePlayerEntity fake)) return;
            if (!fake.getOwnerUUID().equals(player.getUUID())) return;

            BlockState state = level.getBlockState(p.blockPos());
            if (state.isAir()) return;

            double distSq = fake.position().distanceToSqr(Vec3.atCenterOf(p.blockPos()));
            if (distSq <= INTERACT_RANGE * INTERACT_RANGE) {
                triggerInteract(level, player, p.blockPos(), p.direction(), p.location());
            } else {
                fake.setInteractTarget(p.blockPos(), p.direction(), p.location());
            }
        });
    }

    private static void triggerInteract(ServerLevel level, ServerPlayer player, BlockPos pos, Direction dir, Vec3 location) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        Vec3 useLocation = location != null ? location : Vec3.atCenterOf(pos);
        BlockHitResult hit = new BlockHitResult(useLocation, dir, pos, false);
        state.useWithoutItem(level, player, hit);
    }
}
