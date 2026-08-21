package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.TouristNpcHelper;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenManualControlService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 精简游客实体每 tick 的 SimuKraft 附加逻辑：
 * 游客只保留吃饭/进店离开的旅游流程，跳过手动控制，数据同步降频为 20 tick 一次。
 */
@Mixin(value = CitizenEntity.class, remap = false)
public class CitizenEntityTouristMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/citizen/CitizenManualControlService;tick(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/entity/CitizenEntity;)V"),
            require = 0)
    private void nsuk$skipLightNpcManualControl(ServerLevel level, CitizenEntity entity) {
        if (!TouristNpcHelper.isLightNpcEntity(entity, level)) {
            CitizenManualControlService.tick(level, entity);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/citizen/CitizenService;ensureCitizen(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/entity/CitizenEntity;)Lcommon/cn/kafei/simukraft/citizen/CitizenData;"),
            require = 0)
    private CitizenData nsuk$throttleLightNpcEnsure(ServerLevel level, CitizenEntity entity) {
        if (!TouristNpcHelper.isLightNpcEntity(entity, level)) {
            return CitizenService.ensureCitizen(level, entity);
        }
        if (entity.tickCount % 20 == 0) {
            return CitizenService.ensureCitizen(level, entity);
        }
        CitizenData data = CitizenManager.get(level).getCitizen(entity.getUUID()).orElse(null);
        return data != null ? data : CitizenService.ensureCitizen(level, entity);
    }
}