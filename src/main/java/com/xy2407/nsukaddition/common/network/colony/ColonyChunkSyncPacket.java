package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import com.xy2407.nsukaddition.common.network.clientbound.ColonyCoreBridge;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 附属地区块变更同步包，服务端广播区块增减信息给客户端缓存。 */
@SuppressWarnings("null")
public record ColonyChunkSyncPacket(UUID colonyId, String colonyName, String parentCityName,
                                     UUID parentCityId,
                                     List<ChunkCoord> allChunks) implements CustomPacketPayload {

    public record ChunkCoord(int x, int z) {}

    public static final Type<ColonyChunkSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_chunk_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyChunkSyncPacket> STREAM_CODEC =
            StreamCodec.of(ColonyChunkSyncPacket::encode, ColonyChunkSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyChunkSyncPacket p) {
        b.writeUUID(p.colonyId());
        b.writeUtf(p.colonyName(), 128);
        b.writeUtf(p.parentCityName(), 64);
        b.writeUUID(p.parentCityId());
        b.writeVarInt(p.allChunks().size());
        for (ChunkCoord cc : p.allChunks()) {
            b.writeVarInt(cc.x());
            b.writeVarInt(cc.z());
        }
    }

    public static ColonyChunkSyncPacket decode(RegistryFriendlyByteBuf b) {
        UUID colonyId = b.readUUID();
        String colonyName = b.readUtf(128);
        String parentCityName = b.readUtf(64);
        UUID parentCityId = b.readUUID();
        int count = b.readVarInt();
        List<ChunkCoord> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkCoord(b.readVarInt(), b.readVarInt()));
        }
        return new ColonyChunkSyncPacket(colonyId, colonyName, parentCityName, parentCityId, chunks);
    }

    public static void handle(ColonyChunkSyncPacket p, IPayloadContext ctx) {
        List<ColonyCoreOpenResponsePacket.ChunkCoord> converted = p.allChunks().stream()
                .map(cc -> new ColonyCoreOpenResponsePacket.ChunkCoord(cc.x(), cc.z()))
                .toList();
        ctx.enqueueWork(() -> {
            if (p.allChunks().isEmpty()) {
                ColonyCoreBridge.removeColonyChunks(p.colonyId(), converted);
            } else {
                ColonyCoreBridge.syncChunks(p.colonyId(), p.colonyName(), p.parentCityName(),
                        p.parentCityId(), converted);
            }
        });
    }

    public static ColonyChunkSyncPacket createPacket(ServerLevel level, UUID colonyId) {
        ColonyData colony = ColonySqliteStorage.loadColonyById(level, colonyId);
        if (colony == null) return null;

        String parentCityName = "主城";
        CityData parentCity = CityManager.get(level).getCity(colony.parentCityId()).orElse(null);
        if (parentCity != null && parentCity.cityName() != null) {
            parentCityName = parentCity.cityName();
        }

        List<ChunkCoord> chunks = new ArrayList<>();
        for (ColonySqliteStorage.ChunkEntry ce : ColonySqliteStorage.loadChunksByColony(level, colonyId)) {
            chunks.add(new ChunkCoord(ce.x(), ce.z()));
        }

        return new ColonyChunkSyncPacket(colonyId, colony.name(), parentCityName, colony.parentCityId(), chunks);
    }

    public static void sendToPlayer(ServerPlayer player, ServerLevel level, UUID colonyId) {
        ColonyChunkSyncPacket packet = createPacket(level, colonyId);
        if (packet != null) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static void broadcast(ServerLevel level, UUID colonyId) {
        ColonyChunkSyncPacket packet = createPacket(level, colonyId);
        if (packet == null) return;
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static void broadcastRemoval(ServerLevel level, UUID colonyId) {
        UUID emptyId = new UUID(0L, 0L);
        ColonyChunkSyncPacket packet = new ColonyChunkSyncPacket(colonyId, "", "", emptyId, List.of());
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}
