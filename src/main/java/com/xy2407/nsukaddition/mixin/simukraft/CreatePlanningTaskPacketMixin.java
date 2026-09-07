package com.xy2407.nsukaddition.mixin.simukraft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 规划任务创建不再强制“相邻规划盒容器”校验：取料已改由城市物流仓库供给(见 PlannerWorkServiceMixin)，
 * 因此不再因“无相邻容器”拦截任务创建。
 */
@Mixin(value = common.cn.kafei.simukraft.network.planner.CreatePlanningTaskPacket.class, remap = false)
public abstract class CreatePlanningTaskPacketMixin {

    @Redirect(
            method = "handle",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/network/planner/PlannerNetworkValidation;validateAdjacentContainer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
                    remap = false),
            require = 0, allow = 3
    )
    private static BlockPos nsuk$noAdjacentChestGate(ServerLevel level, BlockPos boxPos, BlockPos chestPos) {
        // 返回非 null 消除“无相邻容器”拦截；真实取料/存放由城市物流仓库接替。
        return chestPos != null ? chestPos : boxPos;
    }
}