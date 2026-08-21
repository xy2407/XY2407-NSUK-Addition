package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 从根源拦截被选中(冻结)市民的自主移动：冻结且无 RTS 指令任务时，一律拒绝其 requestMove，
 * 使随机移动(WANDER)、主动去工作(WORK)、回家、自食等在导航源头被停；
 * 有 RTS 活跃任务(移动/攻击命令)的冻结市民仍可移动，不影响 RTS 指挥。
 */
@Mixin(value = CitizenNavigationService.class, remap = false)
public abstract class CitizenNavigationServiceRtsMixin {

    @Inject(method = "requestMove(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;Lcommon/cn/kafei/simukraft/path/MovementIntent;Z)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$blockFreeRoamWhenFrozen(ServerLevel level, UUID citizenId, Vec3 target,
                                                     MovementIntent intent, boolean bypassAdmissionLimits,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (citizenId != null && RtsCitizenTaskManager.isFrozen(citizenId)
                && !RtsCitizenTaskManager.hasActiveTask(citizenId)) {
            cir.setReturnValue(false);
        }
    }
}