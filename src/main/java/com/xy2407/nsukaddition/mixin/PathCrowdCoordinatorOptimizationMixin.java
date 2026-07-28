package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/** 用位置缓存替代getEntitiesOfClass实体扫描，消除每tick昂贵的实体区域查询。 */
@Mixin(targets = "common.cn.kafei.simukraft.path.PathCrowdCoordinator", remap = false)
public class PathCrowdCoordinatorOptimizationMixin {

    @Unique
    private static final ConcurrentMap<String, ConcurrentMap<UUID, Vec3>> NSUK_POSITIONS = new ConcurrentHashMap<>();

    @Inject(method = "record", at = @At("HEAD"), remap = false)
    private static void nsuk$recordPosition(ServerLevel level, UUID citizenId, Vec3 position, Vec3 commandTarget, CallbackInfo ci) {
        nsuk$positions(level).put(citizenId, position);
    }

    @Inject(method = "clear", at = @At("HEAD"), remap = false)
    private static void nsuk$clearPosition(ServerLevel level, UUID citizenId, CallbackInfo ci) {
        ConcurrentMap<UUID, Vec3> positions = NSUK_POSITIONS.get(nsuk$levelKey(level));
        if (positions != null) {
            positions.remove(citizenId);
        }
    }

    @Redirect(method = "shouldYield",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            remap = false)
    private static List<?> nsuk$getFromPositionCache(ServerLevel level, Class<?> clazz, AABB box, Predicate<?> filter) {
        List<Entity> result = new ArrayList<>();
        ConcurrentMap<UUID, Vec3> positions = NSUK_POSITIONS.get(nsuk$levelKey(level));
        if (positions == null) {
            return result;
        }
        for (Map.Entry<UUID, Vec3> entry : positions.entrySet()) {
            if (!box.contains(entry.getValue())) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof CitizenEntity citizen && !citizen.isRemoved()) {
                result.add(entity);
            }
        }
        return result;
    }

    @Inject(method = "cleanup", at = @At("HEAD"), remap = false)
    private static void nsuk$cleanupPositions(ServerLevel level, CallbackInfo ci) {
        ConcurrentMap<UUID, Vec3> positions = NSUK_POSITIONS.get(nsuk$levelKey(level));
        if (positions != null) {
            positions.entrySet().removeIf(e -> level.getEntity(e.getKey()) == null);
        }
    }

    @Unique
    private static String nsuk$levelKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    @Unique
    private static ConcurrentMap<UUID, Vec3> nsuk$positions(ServerLevel level) {
        return NSUK_POSITIONS.computeIfAbsent(nsuk$levelKey(level), k -> new ConcurrentHashMap<>());
    }
}
