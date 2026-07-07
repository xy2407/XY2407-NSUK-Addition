package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import com.xy2407.nsukaddition.common.mining.MiningBoxManager;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxBlock;
import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;
import com.xy2407.nsukaddition.server.building.BuildTaskTrackedState;
import com.xy2407.nsukaddition.server.material.AvailableMaterialCollector;
import com.xy2407.nsukaddition.server.material.BuildingMaterialCalculator;
import com.xy2407.nsukaddition.server.material.WarehouseReserveCollector;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import net.minecraft.world.level.ChunkPos;

import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 侧边栏数据同步服务，定期将城市信息、建材、财务、居民等数据打包发送给客户端。 */
public final class SidebarSyncService {
    private SidebarSyncService() {
    }

    static final long SYNC = 60L;

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % SYNC != 0) {
            return;
        }
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            sync(level, p);
        }
    }

    static void sync(ServerLevel level, ServerPlayer player) {
        var city = CityManager.get(level).getPlayerCity(player.getUUID());
        if (city.isEmpty()) {
            PacketDistributor.sendToPlayer(player, emptyPacket());
            return;
        }

        UUID cid = city.get().cityId();

        List<String> oNames = new ArrayList<>();
        List<String> oPerms = new ArrayList<>();
        for (CityMemberData m : CityManager.get(level).getMembers(cid)) {
            if (m.permissionLevel() != CityPermissionLevel.CITIZEN) {
                oNames.add(m.playerName());
                oPerms.add(m.permissionLevel().name());
            }
        }

        int shop = 0, factory = 0, res = 0, farm = 0, ranch = 0, mine = 0;
        for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
            if (cid.equals(rec.cityId())) {
                switch (rec.category()) {
                    case "commercial" -> shop++;
                    case "industry" -> factory++;
                    case "residential" -> res++;
                    case "breeding" -> ranch++;
                }
            }
        }

        farm = CityPoiManager.get(level).getCityPois(cid, CityPoiType.FARMLAND).size();

        CityChunkManager chunkManager = CityChunkManager.get(level);
        for (var box : MiningBoxManager.get(level).all()) {
            if (level.getBlockState(box.boxPos()).getBlock() instanceof MiningControlBoxBlock
                    && cid.equals(chunkManager.getChunkOwner(new ChunkPos(box.boxPos()).toLong()))) {
                mine++;
            }
        }

        Map<String, Integer> reserveCounts = WarehouseReserveCollector.collectReserve(level, cid);
        List<SidebarSyncPacket.MaterialEntry> reserveMaterials = toSortedEntries(reserveCounts);

        List<SidebarSyncPacket.BuildTaskData> buildTasks = collectBuildTasks(level, player, cid);

        List<SidebarSyncPacket.FinanceEntry> financeEntries = collectFinanceEntries(level, cid);

        List<SidebarSyncPacket.CitizenEntry> citizens = collectCitizens(level, cid);

        PacketDistributor.sendToPlayer(player, new SidebarSyncPacket(
                cid, oNames, oPerms, shop, factory, res, farm, ranch, mine,
                reserveMaterials, buildTasks, financeEntries, citizens));
    }

    private static SidebarSyncPacket emptyPacket() {
        return new SidebarSyncPacket(
                null, List.of(), List.of(), 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
    }

    private static List<SidebarSyncPacket.BuildTaskData> collectBuildTasks(ServerLevel level, ServerPlayer player, UUID cityId) {
        List<SidebarSyncPacket.BuildTaskData> tasks = new ArrayList<>();
        try {
            UUID trackedCitizenId = BuildTaskTrackedState.get(level, cityId);
            List<BuildingTaskData> loaded = SimuSqliteStorage.loadBuildingTasks(level);

            if (trackedCitizenId == null && !loaded.isEmpty()) {
                for (BuildingTaskData t : loaded) {
                    if (cityId.equals(t.cityId())) {
                        String st = t.status();
                        if (!"completed".equals(st) && !"interrupted".equals(st)) {
                            trackedCitizenId = t.citizenId();
                            BuildTaskTrackedState.set(level, cityId, trackedCitizenId);
                            break;
                        }
                    }
                }
            }

            for (BuildingTaskData t : loaded) {
                if (!cityId.equals(t.cityId())) {
                    continue;
                }
                String st = t.status();
                if ("completed".equals(st) || "interrupted".equals(st)) {
                    continue;
                }

                if (BuildTaskTrackedState.isPaused(level, cityId, t.citizenId())) {
                    st = "paused_manual";
                }

                boolean tracked = t.citizenId().equals(trackedCitizenId);
                int tot = t.totalBlocks();
                int progress = tot > 0 ? (int) ((double) t.currentBlockIndex() / tot * 100) : 0;

                Map<String, Integer> totalRequired = BuildingMaterialCalculator.calculateTotalRequirements(t);
                Map<String, Integer> remainingRequired = BuildingMaterialCalculator.calculateRemainingRequirements(t);
                Map<String, Integer> available = AvailableMaterialCollector.collectAvailable(level, player, t, cityId);

                List<SidebarSyncPacket.MaterialEntry> reqEntries = toTaskEntries(totalRequired, remainingRequired);
                List<SidebarSyncPacket.MaterialEntry> availEntries = toTaskEntries(totalRequired, available);

                tasks.add(new SidebarSyncPacket.BuildTaskData(
                        t.displayName(), t.citizenId().toString(), progress, st, tracked,
                        reqEntries, availEntries));
            }
        } catch (Exception ignored) {
        }
        return tasks;
    }

    private static List<SidebarSyncPacket.CitizenEntry> collectCitizens(ServerLevel level, UUID cityId) {
        List<SidebarSyncPacket.CitizenEntry> entries = new ArrayList<>();
        try {
            for (CitizenData citizen : CitizenService.listCitizensByCity(level, cityId)) {
                String jobType = citizen.jobType() != null ? citizen.jobType().name() : "UNEMPLOYED";
                boolean hasHome = citizen.homeId() != null;
                String skinPath = citizen.skinPath() != null ? citizen.skinPath() : "";
                entries.add(new SidebarSyncPacket.CitizenEntry(
                        citizen.name(), citizen.uuid().toString(), jobType, hasHome, skinPath));
            }
        } catch (Exception ignored) {
        }
        return entries;
    }

    private static List<SidebarSyncPacket.MaterialEntry> toSortedEntries(Map<String, Integer> counts) {

        List<SidebarSyncPacket.MaterialEntry> entries = new ArrayList<>();
        for (MaterialCategory category : MaterialCategoryRegistry.getAll()) {
            Integer count = counts.get(category.key());
            if (count != null && count > 0) {
                entries.add(new SidebarSyncPacket.MaterialEntry(category.key(), count));
            }
        }
        return entries;
    }

    private static List<SidebarSyncPacket.MaterialEntry> toTaskEntries(Map<String, Integer> involvedCategories,
                                                                        Map<String, Integer> counts) {
        List<SidebarSyncPacket.MaterialEntry> entries = new ArrayList<>();
        for (MaterialCategory category : MaterialCategoryRegistry.getAll()) {
            if (involvedCategories.containsKey(category.key())) {
                int count = counts.getOrDefault(category.key(), 0);
                entries.add(new SidebarSyncPacket.MaterialEntry(category.key(), count));
            }
        }
        return entries;
    }

    private static List<SidebarSyncPacket.FinanceEntry> collectFinanceEntries(ServerLevel level, UUID cityId) {
        List<SidebarSyncPacket.FinanceEntry> entries = new ArrayList<>();
        try {
            List<FinanceTransactionData> transactions = FinanceLedgerService.recent(level, cityId);
            for (FinanceTransactionData t : transactions) {
                entries.add(new SidebarSyncPacket.FinanceEntry(
                        t.time(),
                        t.actorName() != null ? t.actorName() : "",
                        t.amount(),
                        t.balanceAfter(),
                        t.type().name(),
                        t.reason() != null ? t.reason() : ""));
            }
        } catch (Exception ignored) {
        }
        return entries;
    }
}
