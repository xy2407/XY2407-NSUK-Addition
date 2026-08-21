package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityBuildingStats;
import com.xy2407.nsukaddition.common.city.CityProsperityCache;
import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;
import com.xy2407.nsukaddition.server.building.BuildTaskTrackedState;
import com.xy2407.nsukaddition.server.material.BuildingMaterialCalculator;
import com.xy2407.nsukaddition.server.material.WarehouseReserveCollector;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 侧边栏数据同步服务，每 tick 从统一缓存读取数据并发送给客户端，避免直接访问 SQLite。 */
public final class SidebarSyncService {

    private SidebarSyncService() {
    }

    private static final long SYNC_INTERVAL = 20L;
    private static final long REFRESH_OFFSET = 10L;

    public static void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (time % SYNC_INTERVAL == 0) {
            SidebarDataCache.refreshAsync(level);
        }
        if (time % SYNC_INTERVAL == REFRESH_OFFSET) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                sync(level, p);
            }
        }
    }

    static void sync(ServerLevel level, ServerPlayer player) {
        var city = CityManager.get(level).getPlayerCity(player.getUUID());
        if (city.isEmpty()) {
            PacketDistributor.sendToPlayer(player, emptyPacket());
            return;
        }

        SidebarDataCache.CitySqliteCache cached = SidebarDataCache.get(city.get().cityId());
        if (cached == null) {
            PacketDistributor.sendToPlayer(player, emptyPacket());
            return;
        }

        UUID cityId = city.get().cityId();

        List<String> oNames = new ArrayList<>();
        List<String> oPerms = new ArrayList<>();
        for (CityMemberData m : CityManager.get(level).getMembers(cityId)) {
            if (m.permissionLevel() != CityPermissionLevel.CITIZEN) {
                oNames.add(m.playerName());
                oPerms.add(m.permissionLevel().name());
            }
        }

        CityBuildingStats stats;
        try {
            stats = CityBuildingStats.collect(level, cityId);
        } catch (RuntimeException e) {
            NsukAddition.LOGGER.warn("SidebarSync: CityBuildingStats.collect failed for city {}", cityId, e);
            stats = new CityBuildingStats(0, 0, 0, 0, 0);
        }

        int res = 0;
        try {
            for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
                if (cityId.equals(rec.cityId()) && "residential".equals(rec.category())) {
                    res++;
                }
            }
        } catch (RuntimeException e) {
            NsukAddition.LOGGER.warn("SidebarSync: residential count failed for city {}", cityId, e);
        }
        long prosperity;
        try {
            prosperity = CityProsperityCache.getOrCalculate(level, cityId);
        } catch (RuntimeException e) {
            NsukAddition.LOGGER.warn("SidebarSync: prosperity failed for city {}", cityId, e);
            prosperity = 0L;
        }

        Map<String, Integer> reserveCounts;
        try {
            reserveCounts = WarehouseReserveCollector.collectReserve(level, cityId);
        } catch (RuntimeException e) {
            NsukAddition.LOGGER.warn("SidebarSync: reserve collect failed for city {}", cityId, e);
            reserveCounts = new java.util.HashMap<>();
        }
        List<SidebarSyncPacket.MaterialEntry> reserveMaterials = toSortedEntries(reserveCounts);

        List<SidebarSyncPacket.BuildTaskData> buildTasks = collectBuildTasks(level, cityId, cached.buildingTasks(), reserveCounts);

        List<SidebarSyncPacket.FinanceEntry> financeEntries = new ArrayList<>(cached.financeEntries().size());
        for (SidebarDataCache.SidebarCacheFinanceEntry e : cached.financeEntries()) {
            financeEntries.add(new SidebarSyncPacket.FinanceEntry(
                    e.time(), e.actorName(), e.amount(), e.balanceAfter(), e.type(), e.reason()));
        }

        List<SidebarSyncPacket.CitizenEntry> citizens = new ArrayList<>(cached.citizens().size());
        for (SidebarDataCache.SidebarCacheCitizenEntry c : cached.citizens()) {
            citizens.add(new SidebarSyncPacket.CitizenEntry(
                    c.name(), c.uuid().toString(), c.jobType(), c.hasHome(), c.skinPath(), c.colonyName()));
        }

        PacketDistributor.sendToPlayer(player, new SidebarSyncPacket(
                cityId, oNames, oPerms, stats.shopCount(), stats.factoryCount(), res, stats.farmCount(), stats.ranchCount(), stats.mineCount(),
                prosperity, reserveMaterials, buildTasks, financeEntries, citizens));
    }

    private static SidebarSyncPacket emptyPacket() {
        return new SidebarSyncPacket(
                null, List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0L, List.of(), List.of(), List.of(), List.of());
    }

    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, CachedTaskMaterials> MATERIAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long MATERIAL_RECOMPUTE_INTERVAL = 40L;

    private static final class CachedTaskMaterials {
        final int lastBlockIndex;
        final long lastComputedTick;
        final Map<String, Integer> totalRequired;
        final List<SidebarSyncPacket.MaterialEntry> reqEntries;

        CachedTaskMaterials(int lastBlockIndex, long lastComputedTick, Map<String, Integer> totalRequired,
                            List<SidebarSyncPacket.MaterialEntry> req) {
            this.lastBlockIndex = lastBlockIndex;
            this.lastComputedTick = lastComputedTick;
            this.totalRequired = totalRequired;
            this.reqEntries = req;
        }
    }

    private static List<SidebarSyncPacket.BuildTaskData> collectBuildTasks(
            ServerLevel level, UUID cityId, List<BuildingTaskData> loaded, Map<String, Integer> reserveSnapshot) {
        List<SidebarSyncPacket.BuildTaskData> tasks = new ArrayList<>();
        try {
            Map<UUID, BuildingTaskData> merged = new java.util.LinkedHashMap<>();
            for (BuildingTaskData t : loaded) {
                merged.put(t.taskId(), t);
            }
            for (BuildingTaskData t : com.xy2407.nsukaddition.server.building.BuildingTaskQueueService.runningTasks(level)) {
                merged.put(t.taskId(), t);
            }
            List<BuildingTaskData> allLoaded = new ArrayList<>(merged.values());

            UUID trackedTaskId = BuildTaskTrackedState.getTrackedTask(level, cityId);

            List<BuildingTaskData> cityTasks = new ArrayList<>();
            for (BuildingTaskData t : allLoaded) {
                if (cityId.equals(t.cityId())) {
                    cityTasks.add(t);
                }
            }

            if (trackedTaskId == null && !cityTasks.isEmpty()) {
                cityTasks.sort(Comparator.comparingLong(BuildingTaskData::createdAt));
                for (BuildingTaskData t : cityTasks) {
                    String st = t.status();
                    if (!"completed".equals(st) && !"interrupted".equals(st)) {
                        trackedTaskId = t.taskId();
                        BuildTaskTrackedState.setTrackedTask(level, cityId, trackedTaskId);
                        break;
                    }
                }
            }

            for (BuildingTaskData t : cityTasks) {
                String st = t.status();
                if ("completed".equals(st) || "interrupted".equals(st)) {
                    MATERIAL_CACHE.remove(t.taskId());
                    continue;
                }

                if (BuildTaskTrackedState.isPaused(level, cityId, t.citizenId())) {
                    st = "paused_manual";
                }

                boolean tracked = t.taskId().equals(trackedTaskId);
                int tot = t.totalBlocks();
                int progress = tot > 0 ? (int) ((double) t.currentBlockIndex() / tot * 100) : 0;

                CachedTaskMaterials cached = MATERIAL_CACHE.get(t.taskId());
                List<SidebarSyncPacket.MaterialEntry> reqEntries;
                List<SidebarSyncPacket.MaterialEntry> availEntries;
                long now = level.getGameTime();
                Map<String, Integer> totalRequired;
                boolean fresh = cached != null
                        && now - cached.lastComputedTick < MATERIAL_RECOMPUTE_INTERVAL
                        && cached.lastBlockIndex == t.currentBlockIndex();
                if (fresh) {
                    totalRequired = cached.totalRequired;
                    reqEntries = cached.reqEntries;
                } else {
                    totalRequired = BuildingMaterialCalculator.calculateTotalRequirements(t);
                    Map<String, Integer> remainingRequired = BuildingMaterialCalculator.calculateRemainingRequirements(t);
                    reqEntries = toTaskEntries(totalRequired, remainingRequired);
                    MATERIAL_CACHE.put(t.taskId(), new CachedTaskMaterials(t.currentBlockIndex(), now, totalRequired, reqEntries));
                }
                availEntries = toTaskEntries(totalRequired, reserveSnapshot);

                tasks.add(new SidebarSyncPacket.BuildTaskData(
                        t.taskId().toString(), t.displayName(), t.citizenId().toString(), progress, st, tracked,
                        reqEntries, availEntries));
            }
        } catch (Exception ignored) {
        }
        return tasks;
    }

    private static List<SidebarSyncPacket.MaterialEntry> toSortedEntries(Map<String, Integer> counts) {
        List<SidebarSyncPacket.MaterialEntry> entries = new ArrayList<>();
        for (MaterialCategory category : MaterialCategoryRegistry.getAll()) {
            Integer count = counts.get(category.key());
            if (count != null && count > 0) {
                entries.add(new SidebarSyncPacket.MaterialEntry(category.key(), count));
            }
        }
        Integer upgradeLogs = counts.get(MaterialCategoryRegistry.UPGRADE_LOGS_KEY);
        if (upgradeLogs != null && upgradeLogs > 0) {
            entries.add(new SidebarSyncPacket.MaterialEntry(MaterialCategoryRegistry.UPGRADE_LOGS_KEY, upgradeLogs));
        }
        Integer upgradeStone = counts.get(MaterialCategoryRegistry.UPGRADE_STONE_KEY);
        if (upgradeStone != null && upgradeStone > 0) {
            entries.add(new SidebarSyncPacket.MaterialEntry(MaterialCategoryRegistry.UPGRADE_STONE_KEY, upgradeStone));
        }
        return entries;
    }

    private static List<SidebarSyncPacket.MaterialEntry> toTaskEntries(Map<String, Integer> involvedCategories,
                                                                        Map<String, Integer> counts) {
        List<SidebarSyncPacket.MaterialEntry> entries = new ArrayList<>();
        for (MaterialCategory category : MaterialCategoryRegistry.getAll()) {
            if (!MaterialCategoryRegistry.BASIC_MATERIAL_KEYS.contains(category.key())) {
                continue;
            }
            if (involvedCategories.containsKey(category.key())) {
                int count = counts.getOrDefault(category.key(), 0);
                entries.add(new SidebarSyncPacket.MaterialEntry(category.key(), count));
            }
        }
        for (MaterialCategory category : MaterialCategoryRegistry.getAll()) {
            if (MaterialCategoryRegistry.BASIC_MATERIAL_KEYS.contains(category.key())) {
                continue;
            }
            if (involvedCategories.containsKey(category.key())) {
                int count = counts.getOrDefault(category.key(), 0);
                entries.add(new SidebarSyncPacket.MaterialEntry(category.key(), count));
            }
        }
        return entries;
    }
}
