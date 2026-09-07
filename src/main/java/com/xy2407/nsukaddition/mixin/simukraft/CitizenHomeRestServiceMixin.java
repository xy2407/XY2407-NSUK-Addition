package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.restaurant.RestaurantBoxData;
import com.xy2407.nsukaddition.common.restaurant.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.restaurant.RestaurantConstants;
import com.xy2407.nsukaddition.common.restaurant.RestaurantControlBoxService;
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

import java.util.UUID;

/** NPC 处于跟随/原地待命/被 RTS 选中指挥时拦截"传送回家"，不被强制拉回家。 */
@Mixin(CitizenHomeRestService.class)
public class CitizenHomeRestServiceMixin {

    @Inject(method = "moveOrTeleportHome", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$blockHomeTeleport(ServerLevel level, CitizenData citizen,
                                                          Vec3 homeTarget, CallbackInfoReturnable<Boolean> cir) {
        if (isManuallyPiloted(level, citizen) || isRestaurantWorker(level, citizen)) {
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

    /** 餐厅厨师 / 服务员不回家休息，始终留在餐厅工作。 */
    private static boolean isRestaurantWorker(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null) {
            return false;
        }
        UUID id = citizen.uuid();
        for (RestaurantBoxData data : RestaurantBoxManager.get(level).all()) {
            if (id.equals(uuidOf(RestaurantControlBoxService.findAssignedWorker(
                    level, data.boxPos(), RestaurantConstants.HIRE_ROLE_CHEF)))
                    || id.equals(uuidOf(RestaurantControlBoxService.findAssignedWorker(
                    level, data.boxPos(), RestaurantConstants.HIRE_ROLE_WAITER)))) {
                return true;
            }
        }
        return false;
    }

    private static UUID uuidOf(CitizenData citizen) {
        return citizen != null ? citizen.uuid() : null;
    }
}