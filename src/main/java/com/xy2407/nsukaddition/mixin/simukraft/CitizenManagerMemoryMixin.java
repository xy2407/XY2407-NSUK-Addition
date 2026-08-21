package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.citizen.CitizenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * 修复 CitizenManager 两个性能与内存问题：
 * 1. aiQueue.contains(uuid) 在 ConcurrentLinkedQueue 上是 O(n) 线性扫描，城市人口多时
 *    getOrCreate 每 tick 多次调用造成卡顿。新增 HashSet 影子集合做 O(1) 判定，
 *    所有 offer/poll/remove/clear 入口同步维护该集合。
 * 2. lastHungerDecayTick 在居民死亡 (markCitizenDead) 或移除 (removeCitizen) 时
 *    未清理，导致死亡居民条目永久残留造成内存泄漏。在两个入口 HEAD 注入清理逻辑。
 */
@Mixin(value = CitizenManager.class, remap = false)
public class CitizenManagerMemoryMixin {

    @Shadow
    private ConcurrentLinkedQueue<UUID> aiQueue;

    @Shadow
    private ConcurrentMap<UUID, Long> lastHungerDecayTick;

    @Unique
    private final Set<UUID> nsuk$aiQueueSet = ConcurrentHashMap.newKeySet();

    @Redirect(method = "getOrCreate", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentLinkedQueue;contains(Ljava/lang/Object;)Z"),
            require = 0, allow = 1)
    private boolean nsuk$containsViaShadowSet(ConcurrentLinkedQueue<UUID> queue, Object uuid) {
        return nsuk$aiQueueSet.contains(uuid);
    }

    @Redirect(method = {"putLoadedCitizen", "getOrCreate", "tick"}, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentLinkedQueue;offer(Ljava/lang/Object;)Z"),
            require = 0, allow = 8)
    private boolean nsuk$offerAndSync(ConcurrentLinkedQueue<UUID> queue, Object uuidObj) {
        UUID uuid = (UUID) uuidObj;
        if (uuid != null) {
            nsuk$aiQueueSet.add(uuid);
        }
        return queue.offer(uuid);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentLinkedQueue;poll()Ljava/lang/Object;"),
            require = 0, allow = 1)
    private Object nsuk$pollAndSync(ConcurrentLinkedQueue<UUID> queue) {
        UUID uuid = queue.poll();
        if (uuid != null) {
            nsuk$aiQueueSet.remove(uuid);
        }
        return uuid;
    }

    @Redirect(method = {"markCitizenDead", "removeCitizen"}, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentLinkedQueue;remove(Ljava/lang/Object;)Z"),
            require = 0, allow = 2)
    private boolean nsuk$removeAndSync(ConcurrentLinkedQueue<UUID> queue, Object uuid) {
        nsuk$aiQueueSet.remove(uuid);
        return queue.remove(uuid);
    }

    @Redirect(method = {"reloadFromSqlite", "loadFromSqlite"}, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentLinkedQueue;clear()V"),
            require = 0, allow = 2)
    private void nsuk$clearAndSync(ConcurrentLinkedQueue<UUID> queue) {
        nsuk$aiQueueSet.clear();
        queue.clear();
    }

    @Inject(method = "markCitizenDead", at = @At("HEAD"), require = 0)
    private void nsuk$clearHungerDecayOnDeath(UUID uuid, long deathDay, CallbackInfo ci) {
        if (uuid != null && lastHungerDecayTick != null) {
            lastHungerDecayTick.remove(uuid);
        }
    }

    @Inject(method = "removeCitizen", at = @At("HEAD"), require = 0)
    private void nsuk$clearHungerDecayOnRemove(UUID uuid, CallbackInfo ci) {
        if (uuid != null && lastHungerDecayTick != null) {
            lastHungerDecayTick.remove(uuid);
        }
    }
}