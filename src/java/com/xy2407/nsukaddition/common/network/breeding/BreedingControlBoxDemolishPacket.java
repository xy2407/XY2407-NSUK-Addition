package com.xy2407.nsukaddition.common.network.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 繁育控制箱拆除网络包，处理客户端发送的拆除繁育控制箱请求。 */
@SuppressWarnings("null")
public record BreedingControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<BreedingControlBoxDemolishPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "breeding_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreedingControlBoxDemolishPacket> STREAM_CODEC =
            StreamCodec.of(BreedingControlBoxDemolishPacket::encode, BreedingControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, BreedingControlBoxDemolishPacket p) {
        b.writeBlockPos(p.pos());
    }

    public static BreedingControlBoxDemolishPacket decode(RegistryFriendlyByteBuf b) {
        return new BreedingControlBoxDemolishPacket(b.readBlockPos());
    }

    public static void handle(BreedingControlBoxDemolishPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) {
                return;
            }
            if (!level.getBlockState(p.pos()).is(ModBlocks.BREEDING_CONTROL_BOX.get())) {
                return;
            }
            BreedingControlBoxService.onRemoved(level, p.pos());
            level.setBlockAndUpdate(p.pos(), Blocks.AIR.defaultBlockState());
        }
    }
}
