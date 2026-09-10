package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.server.physicise.BuildingPhysiciseService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 建筑落成时：物理化建筑分类自动装配成可移动的 sable 物理子世界。 */
@Mixin(value = PlacedBuildingService.class, remap = false)
public class BuildingPhysiciseMixin {

    @Inject(method = "register", at = @At("TAIL"), remap = false, require = 0)
    private static void nsuk$physicisePhysicalBuild(ServerLevel level, PlacedBuildingRecord record, CallbackInfo ci) {
        BuildingPhysiciseService.onBuildingRegistered(level, record);
    }
}