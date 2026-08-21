package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.rts.path.SableStructureReader;
import com.xy2407.nsukaddition.common.rts.path.SableTargetTracker;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * CitizenNavigationService.requestMove 桥接：目标点落在 Sable 结构内时登记"结构局部锚点"，
 * 供 ActiveNavigation 每 tick 追踪移动结构；目标点不在结构内则清除旧锚点，退回普通寻路。
 */
@Mixin(targets = "common.cn.kafei.simukraft.path.CitizenNavigationService")
public abstract class CitizenNavigationServiceSableMixin {

    @Inject(method = "requestMove(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;Lcommon/cn/kafei/simukraft/path/MovementIntent;Z)Z",
            at = @At("HEAD"))
    private static void nsukaddition$trackStructureTarget(ServerLevel level, UUID citizenId, Vec3 target,
                                                          MovementIntent intent, boolean bypassAdmissionLimits,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (level == null || citizenId == null || target == null) return;
        SableTargetTracker.track(level, citizenId, SableStructureReader.resolveAnchor(level, target), intent);
    }
}