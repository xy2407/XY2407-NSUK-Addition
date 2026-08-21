package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NPC 处于跟随/原地待命/被 RTS 选中指挥时拦截"复位/传回工作岗位"，不被强制拉回岗位。 */
@Mixin(CitizenWorkplaceMoveService.class)
public class CitizenWorkplaceMoveServiceMixin {

    @Inject(method = "returnToWorkplace", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$blockWorkReturnWhenPiloted(ServerLevel level, CitizenData citizen,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (isManuallyPiloted(level, citizen)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "recoverToWorkplace", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$blockWorkRecoverWhenPiloted(ServerLevel level, CitizenData citizen,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (isManuallyPiloted(level, citizen)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isManuallyPiloted(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null) {
            return false;
        }
        if (RtsCitizenTaskManager.isFrozen(citizen.uuid())) {
            return true;
        }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        return entity != null && (entity.isStayInPlace() || entity.getFollowPlayerId() != null);
    }
}