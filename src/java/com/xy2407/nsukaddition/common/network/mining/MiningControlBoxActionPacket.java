package com.xy2407.nsukaddition.common.network.mining;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 挖矿控制盒操作动作网络包，支持启停与解雇工人等操作。 */
@SuppressWarnings("null")
public record MiningControlBoxActionPacket(BlockPos pos, Action action) implements CustomPacketPayload {

    public static final Type<MiningControlBoxActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "mining_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningControlBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(MiningControlBoxActionPacket::encode, MiningControlBoxActionPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, MiningControlBoxActionPacket p) {
        b.writeBlockPos(p.pos());
        b.writeEnum(p.action());
    }
    public static MiningControlBoxActionPacket decode(RegistryFriendlyByteBuf b) {
        return new MiningControlBoxActionPacket(b.readBlockPos(), b.readEnum(Action.class));
    }

    public static void handle(MiningControlBoxActionPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            if (!level.getBlockState(p.pos()).is(ModBlocks.MINING_CONTROL_BOX.get())) return;
            switch (p.action()) {
                case TOGGLE_RUN -> MiningControlBoxService.toggleRunning(level, p.pos());
                case FIRE -> MiningControlBoxService.fireWorker(level, p.pos());
            }
            PacketDistributor.sendToPlayer(player,
                    MiningControlBoxOpenResponsePacket.from(MiningControlBoxService.buildView(level, p.pos())));
        }
    }

    public enum Action { TOGGLE_RUN, FIRE }
}
