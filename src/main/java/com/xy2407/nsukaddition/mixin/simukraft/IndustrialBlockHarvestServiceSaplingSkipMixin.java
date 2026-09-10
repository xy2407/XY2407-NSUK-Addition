package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.industrial.IndustrialBlockClusterHarvestService;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** 补种某棵缺树苗（或位置不可用）时丢弃该棵、保留其余待补种位置继续，实现缺一棵跳一棵。 */
@Mixin(IndustrialBlockClusterHarvestService.class)
public abstract class IndustrialBlockHarvestServiceSaplingSkipMixin {

    @Redirect(
            method = "plantRemaining",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/industrial/IndustrialBlockClusterHarvestService;remainingWithFirst(Lnet/minecraft/core/BlockPos;Ljava/util/List;)Ljava/util/List;"),
            remap = false
    )
    private static List<BlockPos> nsukaddition$skipFailedPosition(BlockPos first, List<BlockPos> remaining) {
        return remaining;
    }
}