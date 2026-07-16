package com.xy2407.nsukaddition.client.data;

import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 侧边栏数据的客户端不可变快照，存储城市、建筑、财务和居民信息。 */
public final class SidebarDataSnapshot {

    public record Officer(String playerName, String permissionDisplay) {
    }

    public record MaterialStock(String categoryKey, int count) {
    }

    public record BuildTaskMaterial(String categoryKey, int required, int available) {
    }

    public record BuildTask(String displayName, String citizenId, int progressPercent,
                            String statusKey, boolean tracked,
                            List<BuildTaskMaterial> materials) {
    }

    public record FinanceRecord(long time, String actorName, double amount, double balanceAfter,
                                 String type, String reason) {
    }

    public record CitizenRecord(String name, String uuid, String jobType, boolean hasHome, String skinPath, String colonyName) {
        public CitizenRecord(String name, String uuid, String jobType, boolean hasHome, String skinPath) {
            this(name, uuid, jobType, hasHome, skinPath, null);
        }
    }

    private static volatile SidebarDataSnapshot instance = new SidebarDataSnapshot();

    private final UUID cityId;
    private final int shopCount, factoryCount, residenceCount, farmCount, ranchCount, mineCount;
    private final List<Officer> officers;
    private final List<MaterialStock> reserveMaterials;
    private final List<BuildTask> buildTasks;
    private final List<FinanceRecord> financeRecords;
    private final List<CitizenRecord> citizens;

    public SidebarDataSnapshot() {
        this(null, List.of(), 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
    }

    public SidebarDataSnapshot(UUID cityId,
                               List<Officer> officers,
                               int shopCount, int factoryCount, int residenceCount, int farmCount, int ranchCount,
                               int mineCount,
                               List<MaterialStock> reserveMaterials, List<BuildTask> buildTasks,
                               List<FinanceRecord> financeRecords,
                               List<CitizenRecord> citizens) {
        this.cityId = cityId;
        this.officers = List.copyOf(officers);
        this.shopCount = shopCount;
        this.factoryCount = factoryCount;
        this.residenceCount = residenceCount;
        this.farmCount = farmCount;
        this.ranchCount = ranchCount;
        this.mineCount = mineCount;
        this.reserveMaterials = List.copyOf(reserveMaterials);
        this.buildTasks = List.copyOf(buildTasks);
        this.financeRecords = List.copyOf(financeRecords);
        this.citizens = List.copyOf(citizens);
    }

    public static SidebarDataSnapshot get() {
        return instance;
    }

    public static void set(SidebarDataSnapshot s) {
        instance = s;
    }

    public UUID cityId() {
        return cityId;
    }

    public List<Officer> officers() {
        return officers;
    }

    public int shopCount() {
        return shopCount;
    }

    public int factoryCount() {
        return factoryCount;
    }

    public int residenceCount() {
        return residenceCount;
    }

    public int farmCount() {
        return farmCount;
    }

    public int ranchCount() {
        return ranchCount;
    }

    public int mineCount() {
        return mineCount;
    }

    public List<MaterialStock> reserveMaterials() {
        return reserveMaterials;
    }

    public List<BuildTask> buildTasks() {
        return buildTasks;
    }

    public boolean hasBuildTask() {
        return !buildTasks.isEmpty();
    }

    public List<FinanceRecord> financeRecords() {
        return financeRecords;
    }

    public List<CitizenRecord> citizens() {
        return citizens;
    }

    public int reserveCount(String categoryKey) {
        for (MaterialStock stock : reserveMaterials) {
            if (stock.categoryKey().equals(categoryKey)) {
                return stock.count();
            }
        }
        return 0;
    }
}
