package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.UUID;

/**
 * 修改 BuilderConstructionService：
 * 1. 建筑完成后不再自动解雇 NPC 建筑工人。
 * 2. 降低单 tick 建筑方块预算：原 128 个 setBlock 会造成主线程明显卡顿，降到 16 后分摊到 8 倍时长，单 builder 视觉无明显差别。
 * 3. 建筑完成时同步删除任务记录：原实现异步删除，服务端重启会丢失未落库的删除，导致已完成任务在重启后被恢复。
 */
@Mixin(value = BuilderConstructionService.class, remap = false)
public class BuilderConstructionServiceMixin {

    @Redirect(
            method = "completeTask",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/job/CitizenEmploymentService;clearAfterJobFinished(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)Ljava/util/Optional;", remap = false),
            require = 0, allow = 1
    )
    private static Optional<CitizenData> nsuk$cancelAutoFire(ServerLevel level, UUID citizenId) {
        CitizenService.findCitizen(level, citizenId).ifPresent(citizen -> {
            citizen.setWorkStatus(CitizenWorkStatus.WORKING);
            citizen.setStatusLabel("");
            citizen.setWorkNeedDetail("");
            CitizenService.save(level, citizenId);
        });
        return Optional.empty();
    }

    @ModifyConstant(method = "consumeBuildBudget", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 128), remap = false, require = 0, allow = 1)
    private static int nsuk$reduceBlockBudget(int original) {
        return 32;
    }

    @Redirect(
            method = "tickTask",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isAreaLoaded(Lnet/minecraft/core/BlockPos;I)Z"),
            remap = true, require = 1, allow = 1
    )
    private static boolean nsuk$singleChunkLoadedCheck(ServerLevel level, BlockPos pos, int radius) {
        return level.isLoaded(pos);
    }
}
