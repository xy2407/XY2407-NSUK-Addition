package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkSyncPacket;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 附属地核心迁移网络包，仅允许在附属地领地范围内迁移核心方块。 */
@SuppressWarnings("null")
public record ColonyCoreMovePacket(BlockPos oldCorePos, BlockPos newCorePos, UUID colonyId) implements CustomPacketPayload {

    public static final Type<ColonyCoreMovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_core_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyCoreMovePacket> STREAM_CODEC =
            StreamCodec.of(ColonyCoreMovePacket::encode, ColonyCoreMovePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyCoreMovePacket p) {
        b.writeBlockPos(p.oldCorePos());
        b.writeBlockPos(p.newCorePos());
        b.writeUUID(p.colonyId());
    }

    public static ColonyCoreMovePacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyCoreMovePacket(b.readBlockPos(), b.readBlockPos(), b.readUUID());
    }

    public static void handle(ColonyCoreMovePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_NOT_FOUND));
            return;
        }

        if (!colony.corePos().equals(p.oldCorePos())) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_NOT_FOUND));
            return;
        }

        if (p.oldCorePos().equals(p.newCorePos())) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_SAME_POS));
            return;
        }

        BlockState newState = level.getBlockState(p.newCorePos());
        if (!newState.isAir() && !newState.canBeReplaced()) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_POS_OCCUPIED));
            return;
        }

        ChunkPos newChunk = new ChunkPos(p.newCorePos());
        String dimId = level.dimension().location().toString();
        int chunkCount = countChunksAt(level, p.colonyId(), dimId, newChunk.x, newChunk.z);
        if (chunkCount <= 0) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_NOT_IN_TERRITORY));
            return;
        }

        ColonyData updated = new ColonyData(colony.colonyId(), colony.parentCityId(), colony.name(),
                p.newCorePos().immutable(), colony.dimensionId(), colony.createdAt());
        ColonySqliteStorage.saveColony(level, updated);

        level.setBlock(p.newCorePos(), ModBlocks.COLONY_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(p.oldCorePos(), level.getFluidState(p.oldCorePos()).createLegacyBlock(), Block.UPDATE_ALL);

        ColonyChunkSyncPacket.broadcast(level, colony.colonyId());

        InfoToastService.success(player, Component.translatable(ColonyConstants.MSG_MOVE_SUCCESS));
    }

    private static int countChunksAt(ServerLevel level, UUID colonyId, String dimId, int chunkX, int chunkZ) {
        return ColonySqliteStorage.hasChunk(level, colonyId, dimId, chunkX, chunkZ) ? 1 : 0;
    }
}
