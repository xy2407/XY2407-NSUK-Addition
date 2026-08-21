package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.buildbox.BuildingPreviewManager;
import com.xy2407.nsukaddition.client.rts.RtsBuildingListHudLayer;
import com.xy2407.nsukaddition.client.rts.RtsBuildingPlacementManager;
import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import com.xy2407.nsukaddition.common.network.rts.RtsPlacedBuildingSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 复用 simukraft 的 BuildingBoundsRenderer 渲染管线（已在 RTS 独立相机视角验证可正常渲染），
 * 仅放宽渲染条件：
 * 原条件：放置预览激活时渲染城市领地边界。
 * 新条件：放置预览激活 或 RTS 模式选中建筑师时，都渲染城市领地边界 + 已放置建筑界限
 * （已放置建筑界限 renderSelectedBuildingBounds 原本就无条件渲染，数据由服务端经
 * simukraft 的 ResidentialControlBoxBoundsUpdatePacket 同步填充）。
 */
@Mixin(BuildingBoundsRenderer.class)
public abstract class BuildingBoundsRendererRtsMixin {

    @Redirect(method = "onRender",
            at = @At(value = "INVOKE", target = "Lclient/cn/kafei/simukraft/client/buildbox/BuildingPreviewManager;isPreviewActive()Z"),
            remap = false, require = 1)
    private static boolean nsukaddition$cityBoundaryForRtsBuilder(RenderLevelStageEvent event) {
        boolean original = BuildingPreviewManager.isPreviewActive();
        if (original) {
            return true;
        }
        return RtsModeManager.isActive() && RtsBuildingListHudLayer.isBuilderSelected();
    }

    @Redirect(method = "updateDisplayedBuildingBounds",
            at = @At(value = "INVOKE", target = "Lclient/cn/kafei/simukraft/client/buildbox/BuildingBoundsRenderer;isBuildingBoundsVisible(Lnet/minecraft/core/BlockPos;)Z"),
            remap = false, require = 1)
    private static boolean nsukaddition$allowFirstBuildingBounds(BlockPos controlBoxPos) {
        boolean visible = BuildingBoundsRenderer.isBuildingBoundsVisible(controlBoxPos);
        if (visible) {
            return true;
        }
        return RtsModeManager.isActive() && RtsBuildingListHudLayer.isBuilderSelected();
    }

    private static final int NSUK_MOVE_BUILDING_COLOR = 0xFF40E040;

    @ModifyArg(method = {"lambda$renderSelectedBuildingBounds$5", "lambda$renderSelectedBuildingBounds$4"},
            at = @At(value = "INVOKE",
                    target = "Lclient/cn/kafei/simukraft/client/buildbox/BuildingBoundsRenderer;renderWireBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;IZ)V"),
            index = 3, remap = false, require = 1)
    private static int nsukaddition$moveBuildingGreen(PoseStack poseStack, Vec3 cameraPos, AABB bounds, int color, boolean throughWalls) {
        if (isMoveTargetBounds(bounds)) {
            return NSUK_MOVE_BUILDING_COLOR;
        }
        return color;
    }

    private static boolean isMoveTargetBounds(AABB bounds) {
        RtsPlacedBuildingSyncPacket.Entry entry = RtsBuildingPlacementManager.getMoveEntry();
        if (entry == null || entry.minPos() == null || entry.maxPos() == null || bounds == null) {
            return false;
        }
        if (bounds.maxX - bounds.minX < 0.5D && bounds.maxZ - bounds.minZ < 0.5D) {
            return false;
        }
        double centerX = (bounds.minX + bounds.maxX) / 2.0D;
        double centerZ = (bounds.minZ + bounds.maxZ) / 2.0D;
        double entryX = (entry.minPos().getX() + entry.maxPos().getX() + 1) / 2.0D;
        double entryZ = (entry.minPos().getZ() + entry.maxPos().getZ() + 1) / 2.0D;
        return Math.abs(centerX - entryX) < 1.0D && Math.abs(centerZ - entryZ) < 1.0D;
    }
}
