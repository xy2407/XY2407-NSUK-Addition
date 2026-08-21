package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NPC 处于跟随/原地待命/被 RTS 选中指挥时拦截"传送回家"，不被强制拉回家。 */
@Mixin(CitizenHomeRestService.class)
public class CitizenHomeRestServiceMixin {

    @Inject(method = "moveOrTeleportHome", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$blockHomeTeleportWhenPiloted(ServerLevel level, CitizenData citizen,
                                                          Vec3 homeTarget, CallbackInfoReturnable<Boolean> cir) {
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