package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.planning.PlanningPauseState;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/** 手工暂停的规划师在执行层跳过工作推进，用于侧边栏对规划任务的暂停操作。 */
@Mixin(value = PlannerWorkService.class, remap = false)
public class PlannerPauseMixin {

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/citizen/CitizenSelfFeedingService;isSelfFeeding(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)Z",
                    remap = false))
    private static boolean nsuk$skipManualPausedPlanner(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return CitizenSelfFeedingService.isSelfFeeding(level, citizenId);
        }
        return PlanningPauseState.isPaused(level, citizenId) || CitizenSelfFeedingService.isSelfFeeding(level, citizenId);
    }
}