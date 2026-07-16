package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 附属地区块放弃认领网络包，玩家请求放弃一个附属地区块。 */
@SuppressWarnings("null")
public record ColonyChunkAbandonPacket(UUID colonyId, int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final Type<ColonyChunkAbandonPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_chunk_abandon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyChunkAbandonPacket> STREAM_CODEC =
            StreamCodec.of(ColonyChunkAbandonPacket::encode, ColonyChunkAbandonPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyChunkAbandonPacket p) {
        b.writeUUID(p.colonyId());
        b.writeVarInt(p.chunkX());
        b.writeVarInt(p.chunkZ());
    }

    public static ColonyChunkAbandonPacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyChunkAbandonPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(ColonyChunkAbandonPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.colony.not_found"));
            return;
        }

        String dimId = level.dimension().location().toString();
        if (!ColonySqliteStorage.hasChunk(level, p.colonyId(), dimId, p.chunkX(), p.chunkZ())) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_not_owned"));
            return;
        }

        int coreChunkX = colony.corePos().getX() >> 4;
        int coreChunkZ = colony.corePos().getZ() >> 4;
        if (p.chunkX() == coreChunkX && p.chunkZ() == coreChunkZ) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_is_core"));
            return;
        }

        ColonySqliteStorage.removeChunk(level, p.colonyId(), dimId, p.chunkX(), p.chunkZ());

        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos targetChunk = new ChunkPos(p.chunkX(), p.chunkZ());
        UUID owner = chunkMgr.getChunkOwner(targetChunk.toLong());
        if (owner != null && owner.equals(colony.colonyId())) {
            chunkMgr.unclaimChunk(colony.colonyId(), targetChunk.toLong());
        }

        ColonyChunkSyncPacket.broadcast(level, p.colonyId());
        CityChunkSyncService.syncToAll(level);

        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.chunk_abandon_success", p.chunkX(), p.chunkZ()));
    }
}
