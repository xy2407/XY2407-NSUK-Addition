package com.xy2407.nsukaddition.client.data;

import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** 将服务端同步的侧边栏网络包转换为客户端数据快照。 */
@OnlyIn(Dist.CLIENT)
public final class SidebarDataClient {

    private SidebarDataClient() {
    }

    public static void handlePacket(SidebarSyncPacket p) {
        List<SidebarDataSnapshot.Officer> officers = new ArrayList<>();
        for (int i = 0; i < p.officerNames().size(); i++) {
            String perm = i < p.officerPerms().size() ? p.officerPerms().get(i) : "";
            officers.add(new SidebarDataSnapshot.Officer(p.officerNames().get(i), perm));
        }

        List<SidebarDataSnapshot.MaterialStock> reserveMaterials = new ArrayList<>();
        for (SidebarSyncPacket.MaterialEntry entry : p.reserveMaterials()) {
            reserveMaterials.add(new SidebarDataSnapshot.MaterialStock(entry.categoryKey(), entry.count()));
        }

        List<SidebarDataSnapshot.BuildTask> tasks = new ArrayList<>();
        for (SidebarSyncPacket.BuildTaskData task : p.buildTasks()) {
            List<SidebarDataSnapshot.BuildTaskMaterial> materials = new ArrayList<>();
            for (SidebarSyncPacket.MaterialEntry req : task.required()) {
                int available = findAvailable(task.available(), req.categoryKey());
                materials.add(new SidebarDataSnapshot.BuildTaskMaterial(req.categoryKey(), req.count(), available));
            }
            tasks.add(new SidebarDataSnapshot.BuildTask(
                    task.displayName(), task.citizenId(), task.progressPercent(),
                    task.statusKey(), task.tracked(), materials));
        }

        List<SidebarDataSnapshot.FinanceRecord> financeRecords = new ArrayList<>();
        for (SidebarSyncPacket.FinanceEntry entry : p.financeEntries()) {
            financeRecords.add(new SidebarDataSnapshot.FinanceRecord(
                    entry.time(), entry.actorName(), entry.amount(),
                    entry.balanceAfter(), entry.type(), entry.reason()));
        }

        List<SidebarDataSnapshot.CitizenRecord> citizens = new ArrayList<>();
        for (SidebarSyncPacket.CitizenEntry entry : p.citizens()) {
            citizens.add(new SidebarDataSnapshot.CitizenRecord(
                    entry.name(), entry.uuid(), entry.jobType(), entry.hasHome(), entry.skinPath()));
        }

        SidebarDataSnapshot.set(new SidebarDataSnapshot(p.cityId(), officers,
                p.shopCount(), p.factoryCount(), p.residenceCount(), p.farmCount(), p.ranchCount(), p.mineCount(),
                reserveMaterials, tasks, financeRecords, citizens));
    }

    private static int findAvailable(List<SidebarSyncPacket.MaterialEntry> available, String categoryKey) {
        for (SidebarSyncPacket.MaterialEntry entry : available) {
            if (entry.categoryKey().equals(categoryKey)) {
                return entry.count();
            }
        }
        return 0;
    }

    public static void reset() {
        SidebarDataSnapshot.set(new SidebarDataSnapshot());
    }
}
