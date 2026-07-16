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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 挖矿控制盒拆除网络包，客户端请求拆除指定位置的控制盒方块。 */
@SuppressWarnings("null")
public record MiningControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<MiningControlBoxDemolishPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "mining_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiningControlBoxDemolishPacket> STREAM_CODEC =
            StreamCodec.of(MiningControlBoxDemolishPacket::encode, MiningControlBoxDemolishPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, MiningControlBoxDemolishPacket p) { b.writeBlockPos(p.pos()); }
    public static MiningControlBoxDemolishPacket decode(RegistryFriendlyByteBuf b) { return new MiningControlBoxDemolishPacket(b.readBlockPos()); }

    public static void handle(MiningControlBoxDemolishPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            if (!level.getBlockState(p.pos()).is(ModBlocks.MINING_CONTROL_BOX.get())) return;
            MiningControlBoxService.onRemoved(level, p.pos());
            level.setBlock(p.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
