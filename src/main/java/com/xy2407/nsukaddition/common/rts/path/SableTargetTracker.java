package com.xy2407.nsukaddition.common.rts.path;

import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sable 移动结构内目标追踪：让市民持续朝"结构局部点"的当前世界位置移动。
 * requestMove 命中结构点时登记锚点；ActiveNavigation 每 tick 拉取当前世界坐标，
 * 结构漂移超过阈值就对新位置重发路径请求，追着移动结构走而不停在冻结坐标。
 */
public final class SableTargetTracker {

    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final double DRIFT_REPATH_BLOCKS = 1.25D;
    private static final int MAX_ENTRIES_PER_LEVEL = 256;

    private SableTargetTracker() {
    }

    private record Entry(SableStructureReader.StructureAnchor anchor, MovementIntent intent,
                         Vec3 lastGoal, long lastRepathTick) {
    }

    private static final Map<ServerLevel, Map<UUID, Entry>> LEVELS = new ConcurrentHashMap<>();

    public static void track(ServerLevel level, UUID citizenId,
                             SableStructureReader.StructureAnchor anchor, MovementIntent intent) {
        if (level == null || citizenId == null) return;
        Map<UUID, Entry> map = levelMap(level);
        if (anchor == null) {
            map.remove(citizenId);
            return;
        }
        Entry existing = map.get(citizenId);
        if (existing != null && existing.anchor().sublevel() == anchor.sublevel()) {
            map.put(citizenId, new Entry(anchor, intent, existing.lastGoal(), existing.lastRepathTick()));
        } else {
            map.put(citizenId, new Entry(anchor, intent, SableStructureReader.anchorToWorld(anchor), Long.MIN_VALUE));
        }
    }

    public static void follow(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) return;
        Map<UUID, Entry> map = LEVELS.get(level);
        if (map == null) return;
        Entry entry = map.get(citizenId);
        if (entry == null) return;
        Vec3 live = SableStructureReader.anchorToWorld(entry.anchor());
        if (live == null) {
            map.remove(citizenId);
            return;
        }
        long now = level.getGameTime();
        if (entry.lastGoal() != null && live.distanceTo(entry.lastGoal()) < DRIFT_REPATH_BLOCKS) {
            return;
        }
        if (now - entry.lastRepathTick() >= REPATH_INTERVAL_TICKS) {
            CitizenNavigationService.requestMove(level, citizenId, live, entry.intent());
            map.put(citizenId, new Entry(entry.anchor(), entry.intent(), live, now));
        }
    }

    private static Map<UUID, Entry> levelMap(ServerLevel level) {
        Map<UUID, Entry> map = LEVELS.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        if (map.size() > MAX_ENTRIES_PER_LEVEL) {
            map.clear();
        }
        return map;
    }
}