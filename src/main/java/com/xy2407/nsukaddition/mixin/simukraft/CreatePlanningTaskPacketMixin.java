package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.network.planner.CreatePlanningTaskPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 放开创建规划任务时"材料箱必须紧贴建筑盒六面"的强制校验，材料改从物流仓库供给。 */
@Mixin(value = CreatePlanningTaskPacket.class, remap = false)
public class CreatePlanningTaskPacketMixin {

    @Redirect(method = "handle", at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/network/planner/PlannerNetworkValidation;validateAdjacentContainer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            remap = false))
    private static BlockPos nsuk$skipAdjacentContainerCheck(ServerLevel level, BlockPos boxPos, BlockPos chestPos) {
        // 不再要求材料箱紧贴六面：返回建筑盒位置作为占位，避免创建任务被 invalid_material_container 拦截。
        return boxPos;
    }
}