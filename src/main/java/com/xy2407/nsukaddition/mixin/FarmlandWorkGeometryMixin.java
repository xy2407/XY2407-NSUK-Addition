package com.xy2407.nsukaddition.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FarmlandWorkGeometry.workAnchorFor 站位搜索补全修正：
 * 原实现只搜距离 1/√2/2 的 12 个邻格，缺 √5≈2.236 那一圈，侧角格被耕地+农田盒边框围死时
 * 12 个全失败便退回"作物格本身站立"，NPC 站不进作物格导致永远到不了 2.4 格判定而卡死。
 * 这里改为搜索完整可达邻域（含 √5 环），且每个候选都在 ACTION_REACH 内，避免错误落点。
 */
@Mixin(targets = "common.cn.kafei.simukraft.farmland.FarmlandWorkGeometry", remap = false)
public abstract class FarmlandWorkGeometryMixin {

    private static final double ACTION_REACH = 2.4D;

    private static final int[][] EXPANDED_STAND_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}
    };

    private static boolean nsuk$isSafeStandPos(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockState foot = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        BlockState below = level.getBlockState(feet.below());
        if (below.is(Blocks.FARMLAND) || foot.getFluidState().is(FluidTags.LAVA) || head.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return nsuk$isBodyPassable(level, feet, foot)
                && nsuk$isBodyPassable(level, feet.above(), head)
                && !below.getCollisionShape(level, feet.below()).isEmpty();
    }

    private static boolean nsuk$isBodyPassable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty();
    }

    @Inject(method = "workAnchorFor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$workAnchorFor(ServerLevel level, BlockPos boxPos, BlockPos cropPos,
                                           CallbackInfoReturnable<Vec3> cir) {
        Vec3 cropCenter = Vec3.atCenterOf(cropPos);
        double reachSqr = ACTION_REACH * ACTION_REACH;
        for (int[] off : EXPANDED_STAND_OFFSETS) {
            BlockPos feet = new BlockPos(cropPos.getX() + off[0], cropPos.getY(), cropPos.getZ() + off[1]);
            if (nsuk$isSafeStandPos(level, feet)) {
                Vec3 stand = Vec3.atBottomCenterOf(feet);
                if (stand.distanceToSqr(cropCenter) <= reachSqr) {
                    cir.setReturnValue(stand);
                    return;
                }
            }
        }
        BlockPos boxStand = boxPos.above();
        if (nsuk$isSafeStandPos(level, boxStand)
                && Vec3.atBottomCenterOf(boxStand).distanceToSqr(cropCenter) <= reachSqr) {
            cir.setReturnValue(Vec3.atBottomCenterOf(boxStand));
            return;
        }
        cir.setReturnValue(Vec3.atBottomCenterOf(cropPos));
    }
}