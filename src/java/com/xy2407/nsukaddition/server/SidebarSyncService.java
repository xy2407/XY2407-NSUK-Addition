package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.common.city.CityBuildingStats;
import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;
import com.xy2407.nsukaddition.server.building.BuildTaskTrackedState;
import com.xy2407.nsukaddition.server.material.AvailableMaterialCollector;
import com.xy2407.nsukaddition.server.material.BuildingMaterialCalculator;
import com.xy2407.nsukaddition.server.material.WarehouseReserveCollector;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 侧边栏数据同步服务，每 tick 从统一缓存读取数据并发送给客户端，避免直接访问 SQLite。 */
public final class SidebarSyncService {

    private SidebarSyncService() {
    }

    private static final long SYNC_INTERVAL = 40L;

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % SYNC_INTERVAL != 0) {
            return;
        }
        SidebarDataCache.refreshAsync(level);
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

        CityBuildingStats stats = CityBuildingStats.collect(level, cityId);

        int res = 0;
        for (var rec : common.cn.kafei.simukraft.building.PlacedBuildingService.getBuildings(level)) {
            if (cityId.equals(rec.cityId()) && "residential".equals(rec.category())) {
                res++;
            }
        }

        Map<String, Integer> reserveCounts = WarehouseReserveCollector.collectReserve(level, cityId);
        List<SidebarSyncPacket.MaterialEntry> reserveMaterials = toSortedEntries(reserveCounts);

        List<SidebarSyncPacket.BuildTaskData> buildTasks = collectBuildTasks(level, player, cityId, cached.buildingTasks());

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
                reserveMaterials, buildTasks, financeEntries, citizens));
    }

    private static SidebarSyncPacket emptyPacket() {
        return new SidebarSyncPacket(
                null, List.of(), List.of(), 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
    }

    private static List<SidebarSyncPacket.BuildTaskData> collectBuildTasks(
            ServerLevel level, ServerPlayer player, UUID cityId, List<BuildingTaskData> loaded) {
        List<SidebarSyncPacket.BuildTaskData> tasks = new ArrayList<>();
        try {
            UUID trackedCitizenId = BuildTaskTrackedState.get(level, cityId);

            List<BuildingTaskData> cityTasks = new ArrayList<>();
            for (BuildingTaskData t : loaded) {
                if (cityId.equals(t.cityId())) {
                    cityTasks.add(t);
                }
            }

            if (trackedCitizenId == null && !cityTasks.isEmpty()) {
                for (BuildingTaskData t : cityTasks) {
                    String st = t.status();
                    if (!"completed".equals(st) && !"interrupted".equals(st)) {
                        trackedCitizenId = t.citizenId();
                        BuildTaskTrackedState.set(level, cityId, trackedCitizenId);
                        break;
                    }
                }
            }

            for (BuildingTaskData t : cityTasks) {
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
}
