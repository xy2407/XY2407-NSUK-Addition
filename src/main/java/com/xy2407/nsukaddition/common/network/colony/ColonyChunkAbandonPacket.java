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

        // 检查区块是否属于该附属地
        String dimId = level.dimension().location().toString();
        if (!ColonySqliteStorage.hasChunk(level, p.colonyId(), dimId, p.chunkX(), p.chunkZ())) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_not_owned"));
            return;
        }

        // 不允许放弃核心所在区块
        int coreChunkX = colony.corePos().getX() >> 4;
        int coreChunkZ = colony.corePos().getZ() >> 4;
        if (p.chunkX() == coreChunkX && p.chunkZ() == coreChunkZ) {
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_is_core"));
            return;
        }

        // 从附属地移除区块
        ColonySqliteStorage.removeChunk(level, p.colonyId(), dimId, p.chunkX(), p.chunkZ());

        // 从城市区块管理器释放
        CityChunkManager chunkMgr = CityChunkManager.get(level);
        ChunkPos targetChunk = new ChunkPos(p.chunkX(), p.chunkZ());
        UUID owner = chunkMgr.getChunkOwner(targetChunk.toLong());
        if (owner != null && owner.equals(colony.parentCityId())) {
            chunkMgr.unclaimChunk(colony.parentCityId(), targetChunk.toLong());
        }

        // 广播区块变更给所有在线玩家（附属地缓存+城市区块缓存）
        ColonyChunkSyncPacket.broadcast(level, p.colonyId());
        CityChunkSyncService.syncToAll(level);

        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.chunk_abandon_success", p.chunkX(), p.chunkZ()));
    }
}
