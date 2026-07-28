package com.xy2407.nsukaddition.server;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 侧边栏 HUD 的 SQLite 数据缓存，每 40 tick 后台刷新一次，显示只读缓存。 */
public final class SidebarDataCache {

    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);
    private static final ExecutorService READ_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nsuk-sidebar-cache-read");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicReference<Map<UUID, CitySqliteCache>> CACHE =
            new AtomicReference<>(Map.of());

    private SidebarDataCache() {
    }

    public static void refreshAsync(ServerLevel level) {
        if (SHUTDOWN.get()) return;
        if (READ_EXECUTOR.isShutdown()) return;
        List<UUID> cityIds = new ArrayList<>();
        for (CityData city : CityManager.get(level).allCities()) {
            cityIds.add(city.cityId());
        }
        try {
            READ_EXECUTOR.execute(() -> refresh(level, cityIds));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    private static void refresh(ServerLevel level, List<UUID> cityIds) {
        try {
            List<BuildingTaskData> allTasks = SimuSqliteStorage.loadBuildingTasks(level);
            Map<UUID, CitySqliteCache> next = new ConcurrentHashMap<>();
            for (UUID cityId : cityIds) {
                List<BuildingTaskData> cityTasks = new ArrayList<>();
                for (BuildingTaskData t : allTasks) {
                    if (cityId.equals(t.cityId())) {
                        cityTasks.add(t);
                    }
                }
                List<SidebarCacheFinanceEntry> financeEntries = collectFinanceEntries(level, cityId);
                List<SidebarCacheCitizenEntry> citizens = collectCitizens(level, cityId);
                next.put(cityId, new CitySqliteCache(cityTasks, financeEntries, citizens));
            }
            CACHE.set(next);
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("Failed to refresh sidebar SQLite cache", e);
        }
    }

    private static List<SidebarCacheFinanceEntry> collectFinanceEntries(ServerLevel level, UUID cityId) {
        List<SidebarCacheFinanceEntry> entries = new ArrayList<>();
        try {
            List<FinanceTransactionData> transactions = FinanceLedgerService.recent(level, cityId);
            for (FinanceTransactionData t : transactions) {
                entries.add(new SidebarCacheFinanceEntry(
                        t.time(),
                        t.actorName() != null ? t.actorName() : "",
                        t.amount(),
                        t.balanceAfter(),
                        t.type().name(),
                        t.reason() != null ? t.reason() : ""));
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("Failed to collect finance entries for sidebar cache", e);
        }
        return entries;
    }

    private static List<SidebarCacheCitizenEntry> collectCitizens(ServerLevel level, UUID cityId) {
        List<SidebarCacheCitizenEntry> entries = new ArrayList<>();
        try {
            Map<UUID, String> colonyNames = new HashMap<>();
            for (com.xy2407.nsukaddition.common.colony.ColonyData cd
                    : com.xy2407.nsukaddition.common.colony.ColonySqliteStorage.loadColoniesByParentCity(level, cityId)) {
                colonyNames.put(cd.colonyId(), cd.name());
            }

            for (CitizenData citizen : CitizenService.listCitizensByCity(level, cityId)) {
                String jobType = citizen.jobType() != null ? citizen.jobType().name() : "UNEMPLOYED";
                boolean hasHome = citizen.homeId() != null;
                String skinPath = citizen.skinPath() != null ? citizen.skinPath() : "";
                UUID colonyId = com.xy2407.nsukaddition.common.colony.ColonySqliteStorage
                        .getColonyForCitizen(level, citizen.uuid());
                String colonyName = colonyId != null ? colonyNames.getOrDefault(colonyId, "") : "";
                entries.add(new SidebarCacheCitizenEntry(
                        citizen.name(), citizen.uuid(), jobType, hasHome, skinPath, colonyName));
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("Failed to collect citizens for sidebar cache", e);
        }
        return entries;
    }

    public static CitySqliteCache get(UUID cityId) {
        return cityId == null ? null : CACHE.get().get(cityId);
    }

    public static void shutdown() {
        SHUTDOWN.set(true);
        READ_EXECUTOR.shutdown();
        try {
            if (!READ_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                READ_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            READ_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        CACHE.set(Map.of());
    }

    public record CitySqliteCache(
            List<BuildingTaskData> buildingTasks,
            List<SidebarCacheFinanceEntry> financeEntries,
            List<SidebarCacheCitizenEntry> citizens) {
    }

    public record SidebarCacheFinanceEntry(
            long time, String actorName, double amount,
            double balanceAfter, String type, String reason) {
    }

    public record SidebarCacheCitizenEntry(
            String name, UUID uuid, String jobType,
            boolean hasHome, String skinPath, String colonyName) {
    }
}
