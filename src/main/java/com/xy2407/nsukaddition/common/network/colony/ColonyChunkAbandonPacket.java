package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyChunkService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            InfoToastService.warning(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.not_found"));
            return;
        }

        if (ColonyChunkService.abandonChunk(level, player, colony, p.chunkX(), p.chunkZ())) {
            ColonyChunkService.broadcastAfterChange(level, colony);
            InfoToastService.success(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_abandon_success", p.chunkX(), p.chunkZ()));
            }
    }
}
