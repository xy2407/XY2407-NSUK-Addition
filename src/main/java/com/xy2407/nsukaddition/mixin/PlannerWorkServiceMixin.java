package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.UUID;

/** 修改 PlannerWorkService：规划完成后不再自动解雇 NPC 规划工人。 */
@Mixin(value = PlannerWorkService.class, remap = false)
public class PlannerWorkServiceMixin {

    @Redirect(
            method = "completeTask",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/job/CitizenEmploymentService;clearAfterJobFinished(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)Ljava/util/Optional;", remap = false),
            require = 0, allow = 1
    )
    private static Optional<CitizenData> nsuk$cancelAutoFire(ServerLevel level, UUID citizenId) {
        return Optional.empty();
    }
}
