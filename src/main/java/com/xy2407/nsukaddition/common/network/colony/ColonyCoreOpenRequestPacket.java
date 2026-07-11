package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyConstants;
import com.xy2407.nsukaddition.common.colony.ColonyCreateService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

/** 附属地核心方块打开请求网络包，客户端请求服务端返回附属地界面数据。 */
@SuppressWarnings("null")
public record ColonyCoreOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ColonyCoreOpenRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_core_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyCoreOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(ColonyCoreOpenRequestPacket::encode, ColonyCoreOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyCoreOpenRequestPacket p) {
        b.writeBlockPos(p.pos());
    }

    public static ColonyCoreOpenRequestPacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyCoreOpenRequestPacket(b.readBlockPos());
    }

    public static void handle(ColonyCoreOpenRequestPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, p.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            InfoToastService.warning(player, Component.translatable(ColonyConstants.MSG_TOO_FAR));
            return;
        }

        String dimId = level.dimension().location().toString();
        ColonyData colony = ColonySqliteStorage.loadColonyByCorePos(level, dimId, pos.asLong());
        if (colony == null) {
            sendNotCreatedResponse(level, player, pos);
            return;
        }

        int usedChunks = ColonySqliteStorage.countChunksByColony(level, colony.colonyId());
        int totalPool = ColonyCreateService.getTotalChunkPool(level, colony.parentCityId());
        int usedPool = ColonyCreateService.getUsedChunkPool(level, colony.parentCityId());
        int citizenCount = ColonySqliteStorage.loadCitizensByColony(level, colony.colonyId()).size();

        CityManager cityMgr = CityManager.get(level);
        CityData parentCity = cityMgr.getCity(colony.parentCityId()).orElse(null);

        boolean canManage = parentCity != null
                && parentCity.hasPermission(player.getUUID(), CityPermissionLevel.MAYOR);

        List<ColonyCoreOpenResponsePacket.OtherTerritoryCitizen> otherCitizens = new ArrayList<>();
        if (parentCity != null) {
            CitizenManager citizenMgr = CitizenManager.get(level);
            UUID cityId = parentCity.cityId();
            for (CitizenData cd : citizenMgr.allCitizens()) {
                if (cd.dead() || !cityId.equals(cd.cityId())) continue;
                UUID assignedColony = ColonySqliteStorage.getColonyForCitizen(level, cd.uuid());
                if (assignedColony == null) {
                    otherCitizens.add(new ColonyCoreOpenResponsePacket.OtherTerritoryCitizen(
                            cd.uuid(), cd.name(), cd.jobType().name(),
                            parentCity.cityName() != null ? parentCity.cityName() : "主城"
                    ));
                }
            }
        }

        List<ColonyData> allColonies = ColonySqliteStorage.loadColoniesByParentCity(level, colony.parentCityId());
        for (ColonyData otherColony : allColonies) {
            if (otherColony.colonyId().equals(colony.colonyId())) continue;
            List<UUID> otherCitizenIds = ColonySqliteStorage.loadCitizensByColony(level, otherColony.colonyId());
            for (UUID citizenUuid : otherCitizenIds) {
                CitizenData cd = CitizenService.findCitizen(level, citizenUuid).orElse(null);
                if (cd != null) {
                    otherCitizens.add(new ColonyCoreOpenResponsePacket.OtherTerritoryCitizen(
                            cd.uuid(), cd.name(), cd.jobType().name(), otherColony.name()
                    ));
                }
            }
        }

        List<ColonyCoreOpenResponsePacket.LocalCitizen> localCitizens = new ArrayList<>();
        List<UUID> localCitizenIds = ColonySqliteStorage.loadCitizensByColony(level, colony.colonyId());
        CityPoiManager poiMgr = CityPoiManager.get(level);
        for (UUID citizenUuid : localCitizenIds) {
            CitizenData cd = CitizenService.findCitizen(level, citizenUuid).orElse(null);
            if (cd != null) {
                String homeName = "";
                if (cd.homeId() != null) {
                    CityPoiData poi = poiMgr.getPoi(cd.homeId());
                    if (poi != null) homeName = poi.type().name() + " @ " + poi.pos().getX() + ", " + poi.pos().getY() + ", " + poi.pos().getZ();
                }
                localCitizens.add(new ColonyCoreOpenResponsePacket.LocalCitizen(
                        cd.uuid(), cd.name(),
                        cd.jobType().translationKey(), cd.workStatusType().translationKey(),
                        cd.age(), cd.gender(), cd.skinPath(),
                        cd.homeId() != null, homeName
                ));
            }
        }

        String parentCityName = parentCity != null && parentCity.cityName() != null
                ? parentCity.cityName() : "主城";

        List<ColonyCoreOpenResponsePacket.ChunkCoord> colonyChunks = new ArrayList<>();
        for (ColonySqliteStorage.ChunkEntry ce : ColonySqliteStorage.loadChunksByColony(level, colony.colonyId())) {
            colonyChunks.add(new ColonyCoreOpenResponsePacket.ChunkCoord(ce.x(), ce.z()));
        }

        PacketDistributor.sendToPlayer(player, new ColonyCoreOpenResponsePacket(
                pos, true, false, canManage,
                colony.colonyId(), colony.parentCityId(), colony.name(),
                colony.corePos(), usedChunks, totalPool, usedPool, citizenCount,
                otherCitizens, localCitizens, parentCityName, colonyChunks
        ));
    }

    /** 附属地尚未创建时返回未创建响应，让客户端显示创建面板。 */
    private static void sendNotCreatedResponse(ServerLevel level, ServerPlayer player, BlockPos pos) {
        CityData city = CityService.findManagedPlayerCity(level, player.getUUID()).orElse(null);
        boolean canCreate = city != null
                && city.hasPermission(player.getUUID(), CityPermissionLevel.MAYOR);
        UUID emptyId = new UUID(0L, 0L);
        PacketDistributor.sendToPlayer(player, new ColonyCoreOpenResponsePacket(
                pos, false, canCreate, false,
                emptyId, emptyId, "",
                pos, 0, 0, 0, 0,
                List.of(), List.of(), "", List.of()
        ));
    }
}
