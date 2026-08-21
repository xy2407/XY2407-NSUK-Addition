package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.TouristNpcHelper;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenDroppedFoodService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 每 NPC 每 tick 的地面食物查询降频为 20 tick 一次，游客直接跳过（游客由旅游服务安排就餐）。 */
@Mixin(value = CitizenEntity.class, remap = false)
public class CitizenEntityEatIntervalMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/citizen/CitizenDroppedFoodService;tryEatNearbyFood(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/entity/CitizenEntity;Lcommon/cn/kafei/simukraft/citizen/CitizenData;)V"),
            require = 0)
    private void nsuk$intervalEat(ServerLevel level, CitizenEntity entity, CitizenData data) {
        long gameTime = level.getGameTime();
        if (Math.floorMod(entity.getUUID().getLeastSignificantBits(), 20L) == gameTime % 20L
                && !TouristNpcHelper.isLightNpcEntity(entity, level)) {
            CitizenDroppedFoodService.tryEatNearbyFood(level, entity, data);
        }
    }
}