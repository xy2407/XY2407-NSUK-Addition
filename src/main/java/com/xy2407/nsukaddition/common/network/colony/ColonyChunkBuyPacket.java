package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyChunkService;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
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

/** 附属地区块购买网络包，玩家请求购买一个区块扩展附属地领地。 */
@SuppressWarnings("null")
public record ColonyChunkBuyPacket(UUID colonyId, int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final Type<ColonyChunkBuyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_chunk_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyChunkBuyPacket> STREAM_CODEC =
            StreamCodec.of(ColonyChunkBuyPacket::encode, ColonyChunkBuyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyChunkBuyPacket p) {
        b.writeUUID(p.colonyId());
        b.writeVarInt(p.chunkX());
        b.writeVarInt(p.chunkZ());
    }

    public static ColonyChunkBuyPacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyChunkBuyPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(ColonyChunkBuyPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_MOVE_NOT_FOUND));
            return;
        }

        if (ColonyChunkService.buyChunk(level, player, colony, p.chunkX(), p.chunkZ())) {
            ColonyChunkService.broadcastAfterChange(level, colony);
            InfoToastService.success(player, Component.translatable(
                    "message.xy2407_nsuk_addition.colony.chunk_buy_success", p.chunkX(), p.chunkZ()));
        }
    }
}
