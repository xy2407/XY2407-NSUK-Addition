package com.xy2407.nsukaddition.common.network.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** 繁育控制箱操作网络包，处理客户端发送的配方选择、运行切换、解雇、修复等操作。 */
@SuppressWarnings("null")
public record BreedingControlBoxActionPacket(BlockPos pos, Action action, String recipeId) implements CustomPacketPayload {

    public static final Type<BreedingControlBoxActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "breeding_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreedingControlBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(BreedingControlBoxActionPacket::encode, BreedingControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, BreedingControlBoxActionPacket p) {
        buf.writeBlockPos(p.pos());
        buf.writeEnum(p.action());
        buf.writeUtf(p.recipeId(), 128);
    }

    public static BreedingControlBoxActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new BreedingControlBoxActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(128));
    }

    public static void handle(BreedingControlBoxActionPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) {
                return;
            }
            if (!level.getBlockState(p.pos()).is(ModBlocks.BREEDING_CONTROL_BOX.get())) {
                return;
            }
            switch (p.action()) {
                case SELECT_RECIPE -> BreedingControlBoxService.selectRecipe(level, p.pos(), p.recipeId());
                case TOGGLE_RUN -> BreedingControlBoxService.toggleRunning(level, p.pos());
                case FIRE -> BreedingControlBoxService.fireWorker(level, p.pos());
                case REPAIR_BUILDING -> repairBuilding(level, player, p.pos());
            }
            PacketDistributor.sendToPlayer(player,
                    BreedingControlBoxOpenResponsePacket.from(BreedingControlBoxService.buildView(level, p.pos())));
        }
    }

    private static void repairBuilding(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlacedBuildingRecord building = BreedingControlBoxService.resolveBuilding(level, pos);
        BuildingIntegrityService.repair(level, player, building);
    }

    public enum Action {
        SELECT_RECIPE,
        TOGGLE_RUN,
        FIRE,
        REPAIR_BUILDING
    }
}
