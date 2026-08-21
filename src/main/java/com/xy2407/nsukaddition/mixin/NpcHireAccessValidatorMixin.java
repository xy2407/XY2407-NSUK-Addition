package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 扩展NpcHireAccessValidator以识别餐厅、养殖和采矿控制箱的雇佣来源和距离提示。 */
@Mixin(targets = "common.cn.kafei.simukraft.network.npc.hire.NpcHireAccessValidator")
public class NpcHireAccessValidatorMixin {

    @Inject(method = "resolveCityId", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onResolveCityId(ServerLevel level, BlockPos sourcePos, String sourceType, String role, CallbackInfoReturnable<UUID> cir) {
        if (cir.getReturnValue() != null) return;

        if (RestaurantConstants.HIRE_SOURCE_TYPE.equals(sourceType)
                && (RestaurantConstants.HIRE_ROLE_CHEF.equals(role) || RestaurantConstants.HIRE_ROLE_WAITER.equals(role))
                && level.getBlockState(sourcePos).is(ModBlocks.RESTAURANT_CONTROL_BOX.get())) {
            cir.setReturnValue(CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong()));
            return;
        }

        if (BreedingConstants.HIRE_SOURCE_TYPE.equals(sourceType)
                && BreedingConstants.HIRE_ROLE.equals(role)
                && level.getBlockState(sourcePos).is(ModBlocks.BREEDING_CONTROL_BOX.get())) {
            cir.setReturnValue(CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong()));
            return;
        }
    }

    @Inject(method = "tooFarMessage", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onTooFarMessage(String sourceType, CallbackInfoReturnable<String> cir) {
        if (RestaurantConstants.HIRE_SOURCE_TYPE.equals(sourceType)) {
            cir.setReturnValue(RestaurantConstants.TOO_FAR_MESSAGE);
        } else if (BreedingConstants.HIRE_SOURCE_TYPE.equals(sourceType)) {
            cir.setReturnValue(BreedingConstants.TOO_FAR_MESSAGE);
        }
    }

    @Redirect(
            method = "validateSource",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/city/CityService;canManageCity(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Ljava/util/UUID;)Z"),
            remap = false, require = 1, allow = 1
    )
    private static boolean nsuk$canManageColonySource(ServerLevel level, UUID cityId, UUID playerUuid) {
        if (CityService.canManageCity(level, cityId, playerUuid)) {
            return true;
        }
        ColonyData colony = ColonySqliteStorage.loadColonyById(level, cityId);
        if (colony != null && colony.parentCityId() != null) {
            return CityService.canManageCity(level, colony.parentCityId(), playerUuid);
        }
        return false;
    }

    @Redirect(
            method = "belongsToSourceCity",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/citizen/CitizenData;cityId()Ljava/util/UUID;"),
            remap = false, require = 1, allow = 1
    )
    private static UUID nsuk$effectiveCityId(CitizenData citizen) {
        if (citizen == null) {
            return null;
        }
        UUID colonyId = ColonySqliteStorage.getColonyForCitizen(citizen.uuid());
        return colonyId != null ? colonyId : citizen.cityId();
    }
}
