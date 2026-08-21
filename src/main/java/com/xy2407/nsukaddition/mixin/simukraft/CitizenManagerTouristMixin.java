package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.TouristNpcHelper;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * 游客/商队/村庄城市 NPC 退出 SimuKraft AI 队列：tick 取出数据时返回 null 直接跳过，
 * 不再执行饥饿衰减/状态覆盖等居民逻辑，且不重新入队，不挤占 AI 预算。
 */
@Mixin(value = CitizenManager.class, remap = false)
public class CitizenManagerTouristMixin {

    @Shadow
    private volatile ServerLevel level;

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentMap;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 0, allow = 1)
    private Object nsuk$skipLightNpcData(ConcurrentMap<UUID, CitizenData> map, Object uuidObj) {
        UUID uuid = (UUID) uuidObj;
        CitizenData data = map.get(uuid);
        if (data == null) return null;
        if (level != null && level.getEntity(uuid) instanceof CitizenEntity entity
                && (TouristNpcHelper.isTouristEntity(entity) || TouristNpcHelper.isCaravanEntity(entity))) {
            return null;
        }
        return TouristNpcHelper.isLightNpc(data, level) ? null : data;
    }
}