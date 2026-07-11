package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.ColonyCoreBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 附属地核心方块打开响应网络包，服务端返回附属地完整界面数据及可迁移市民列表。 */
@SuppressWarnings("null")
public record ColonyCoreOpenResponsePacket(

        BlockPos corePos,

        boolean hasColony,

        boolean canCreateColony,

        boolean canManageColony,

        UUID colonyId,

        UUID parentCityId,

        String name,

        BlockPos colonyCorePos,

        int usedChunks,

        int totalChunkPool,

        int usedPoolChunks,

        int citizenCount,

        List<OtherTerritoryCitizen> otherCitizens,

        List<LocalCitizen> localCitizens,

        String parentCityName,

        List<ChunkCoord> colonyChunks

) implements CustomPacketPayload {

    /** 其它领地的市民信息，用于人口迁移面板展示。 */
    public record OtherTerritoryCitizen(
            UUID citizenId,
            String citizenName,
            String jobType,
            String sourceTerritory
    ) {}

    /** 本附属地的市民信息，参照 CityCitizenManageResponsePacket.CitizenEntry。 */
    public record LocalCitizen(
            UUID citizenId,
            String citizenName,
            String jobKey,
            String workStatusKey,
            int age,
            String gender,
            String skinPath,
            boolean hasHome,
            String homeName
    ) {}

    /** 区块坐标，用于客户端同步附属地区块。 */
    public record ChunkCoord(int x, int z) {}

    public static final Type<ColonyCoreOpenResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_core_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyCoreOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(ColonyCoreOpenResponsePacket::encode, ColonyCoreOpenResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyCoreOpenResponsePacket p) {
        b.writeBlockPos(p.corePos());
        b.writeBoolean(p.hasColony());
        b.writeBoolean(p.canCreateColony());
        b.writeBoolean(p.canManageColony());
        b.writeUUID(p.colonyId());
        b.writeUUID(p.parentCityId());
        b.writeUtf(p.name(), 128);
        b.writeBlockPos(p.colonyCorePos());
        b.writeVarInt(p.usedChunks());
        b.writeVarInt(p.totalChunkPool());
        b.writeVarInt(p.usedPoolChunks());
        b.writeVarInt(p.citizenCount());
        b.writeVarInt(p.otherCitizens().size());
        for (OtherTerritoryCitizen c : p.otherCitizens()) {
            b.writeUUID(c.citizenId());
            b.writeUtf(c.citizenName(), 64);
            b.writeUtf(c.jobType(), 32);
            b.writeUtf(c.sourceTerritory(), 64);
        }
        b.writeVarInt(p.localCitizens().size());
        for (LocalCitizen c : p.localCitizens()) {
            b.writeUUID(c.citizenId());
            b.writeUtf(c.citizenName(), 64);
            b.writeUtf(c.jobKey(), 128);
            b.writeUtf(c.workStatusKey(), 128);
            b.writeVarInt(c.age());
            b.writeUtf(c.gender(), 16);
            b.writeUtf(c.skinPath() != null ? c.skinPath() : "", 256);
            b.writeBoolean(c.hasHome());
            b.writeUtf(c.homeName() != null ? c.homeName() : "", 96);
        }
        b.writeUtf(p.parentCityName(), 64);
        b.writeVarInt(p.colonyChunks().size());
        for (ChunkCoord cc : p.colonyChunks()) {
            b.writeVarInt(cc.x());
            b.writeVarInt(cc.z());
        }
    }

    public static ColonyCoreOpenResponsePacket decode(RegistryFriendlyByteBuf b) {
        BlockPos corePos = b.readBlockPos();
        boolean hasColony = b.readBoolean();
        boolean canCreateColony = b.readBoolean();
        boolean canManageColony = b.readBoolean();
        UUID colonyId = b.readUUID();
        UUID parentCityId = b.readUUID();
        String name = b.readUtf(128);
        BlockPos colonyCorePos = b.readBlockPos();
        int usedChunks = b.readVarInt();
        int totalChunkPool = b.readVarInt();
        int usedPoolChunks = b.readVarInt();
        int citizenCount = b.readVarInt();
        int otherCount = b.readVarInt();
        List<OtherTerritoryCitizen> otherCitizens = new ArrayList<>(otherCount);
        for (int i = 0; i < otherCount; i++) {
            otherCitizens.add(new OtherTerritoryCitizen(
                    b.readUUID(), b.readUtf(64), b.readUtf(32), b.readUtf(64)
            ));
        }
        int localCount = b.readVarInt();
        List<LocalCitizen> localCitizens = new ArrayList<>(localCount);
        for (int i = 0; i < localCount; i++) {
            localCitizens.add(new LocalCitizen(
                    b.readUUID(), b.readUtf(64), b.readUtf(128), b.readUtf(128),
                    b.readVarInt(), b.readUtf(16), b.readUtf(256),
                    b.readBoolean(), b.readUtf(96)
            ));
        }
        String parentCityName = b.readUtf(64);
        int chunkCount = b.readVarInt();
        List<ChunkCoord> colonyChunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            colonyChunks.add(new ChunkCoord(b.readVarInt(), b.readVarInt()));
        }
        return new ColonyCoreOpenResponsePacket(
                corePos, hasColony, canCreateColony, canManageColony,
                colonyId, parentCityId, name, colonyCorePos,
                usedChunks, totalChunkPool, usedPoolChunks, citizenCount,
                otherCitizens, localCitizens, parentCityName, colonyChunks
        );
    }

    public static void handle(ColonyCoreOpenResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ColonyCoreBridge.open(p));
    }
}
