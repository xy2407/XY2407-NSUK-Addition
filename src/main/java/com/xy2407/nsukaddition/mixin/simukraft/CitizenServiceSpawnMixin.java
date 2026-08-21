package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.citizen.CitizenService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 CitizenService.spawnCitizen 的重复添加：同一 UUID 实体已存在于世界时丢弃新实体，
 * 避免 ServerEntityManager 的 "UUID of added entity already exists" warn 与实体膨胀卡顿。
 */
@Mixin(CitizenService.class)
public abstract class CitizenServiceSpawnMixin {

    @Redirect(method = "spawnCitizen(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;Z)Ljava/util/Optional;", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean nsukaddition$addFreshIfAbsent(ServerLevel level, Entity entity) {
        if (level.getEntity(entity.getUUID()) != null) {
            entity.discard();
            return false;
        }
        return level.addFreshEntity(entity);
    }
}