package com.xy2407.nsukaddition.client.rts;

import com.xy2407.nsukaddition.common.network.rts.RtsPlacedBuildingSyncPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsPlacedBuildingSyncRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端已放置建筑缓存:选中建筑师/规划师时向服务端请求该城市建筑列表,供界限渲染与建筑捕获。
 */
@OnlyIn(Dist.CLIENT)
public final class RtsPlacedBuildingCache {

    private static final List<RtsPlacedBuildingSyncPacket.Entry> BUILDINGS = new CopyOnWriteArrayList<>();
    private static final List<Long> CITY_CHUNKS = new CopyOnWriteArrayList<>();
    private static UUID syncedCitizenId;
    private static UUID lastRequestTarget;
    private static long lastRequestAttempt = Long.MIN_VALUE;
    private static final long REQUEST_RETRY_TICKS = 20L;

    private RtsPlacedBuildingCache() {
    }

    public static void applySync(List<RtsPlacedBuildingSyncPacket.Entry> list, List<Long> cityChunks) {
        BUILDINGS.clear();
        if (list != null) {
            BUILDINGS.addAll(list);
        }
        CITY_CHUNKS.clear();
        if (cityChunks != null) {
            CITY_CHUNKS.addAll(cityChunks);
        }
        syncedCitizenId = lastRequestTarget;
    }

    public static List<RtsPlacedBuildingSyncPacket.Entry> getBuildings() {
        return List.copyOf(BUILDINGS);
    }

    public static List<Long> getCityChunks() {
        return List.copyOf(CITY_CHUNKS);
    }

    public static void onSelectionChanged(Set<UUID> selected) {
        UUID target = findBuilderOrPlanner(selected);
        if (target == null) {
            syncedCitizenId = null;
            lastRequestTarget = null;
            BUILDINGS.clear();
            CITY_CHUNKS.clear();
            return;
        }
        long now = currentGameTime();
        lastRequestTarget = target;
        lastRequestAttempt = now;
        PacketDistributor.sendToServer(new RtsPlacedBuildingSyncRequestPacket(target));
    }

    public static boolean hasBuilderOrPlanner(Set<UUID> selected) {
        UUID target = findBuilderOrPlanner(selected);
        if (target == null) {
            return false;
        }
        long now = currentGameTime();
        boolean needsSync = !target.equals(syncedCitizenId)
                || (BUILDINGS.isEmpty() && CITY_CHUNKS.isEmpty());
        if (needsSync && now - lastRequestAttempt >= REQUEST_RETRY_TICKS) {
            lastRequestAttempt = now;
            lastRequestTarget = target;
            PacketDistributor.sendToServer(new RtsPlacedBuildingSyncRequestPacket(target));
        }
        return true;
    }

    private static long currentGameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    private static UUID findBuilderOrPlanner(Set<UUID> selected) {
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        return RtsBuildingListHudLayer.selectedBuilderId();
    }

    public static RtsPlacedBuildingSyncPacket.Entry pickBuildingAt(Vec3 hit) {
        if (hit == null) {
            return null;
        }
        for (var e : BUILDINGS) {
            if (e.minPos() == null || e.maxPos() == null) {
                continue;
            }
            double x = hit.x, y = hit.y, z = hit.z;
            if (x >= e.minPos().getX() - 0.5 && x < e.maxPos().getX() + 1.5
                    && z >= e.minPos().getZ() - 0.5 && z < e.maxPos().getZ() + 1.5
                    && y >= e.minPos().getY() - 1.0 && y < e.maxPos().getY() + 1.5) {
                return e;
            }
        }
        return null;
    }
}