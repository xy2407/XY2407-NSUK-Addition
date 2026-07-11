package com.xy2407.nsukaddition.client.colony;

import com.xy2407.nsukaddition.common.network.colony.ColonyCoreMovePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 附属地核心迁移客户端状态管理，参照 CityCoreMovePreview 实现预览模式开关、ghost位置、右键拦截、Enter确认。 */
@OnlyIn(Dist.CLIENT)
public final class ColonyCoreMovePreview {

    // 是否处于迁移预览模式
    private static boolean active = false;

    // ghost 方块位置（同时只能存在一个）
    private static BlockPos ghostPos = null;

    // 原附属地核心位置
    private static BlockPos sourcePos = null;

    // 附属地 ID
    private static UUID colonyId = null;

    private ColonyCoreMovePreview() {}

    public static boolean isActive() { return active; }
    public static BlockPos getGhostPos() { return ghostPos; }

    // 进入迁移预览模式
    public static void enter(BlockPos pos, UUID id) {
        active = true;
        ghostPos = null;
        sourcePos = pos.immutable();
        colonyId = id;
    }

    // 退出迁移预览模式
    public static void exit() {
        active = false;
        ghostPos = null;
        sourcePos = null;
        colonyId = null;
    }

    // 右键处理：在目标方块对应面放置 ghost，返回 true 表示已拦截
    public static boolean onRightClick() {
        if (!active) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return true;

        BlockHitResult blockHit = (BlockHitResult) hit;
        Direction face = blockHit.getDirection();
        BlockPos clickedPos = blockHit.getBlockPos();
        BlockPos placePos = clickedPos.relative(face);

        ClientLevel level = mc.level;
        if (level != null) {
            var state = level.getBlockState(placePos);
            if (!state.isAir() && !state.canBeReplaced()) {
                return true;
            }
        }

        ghostPos = placePos.immutable();
        return true;
    }

    // Enter 确认：发送迁移包到服务端
    public static boolean onConfirm() {
        if (!active || ghostPos == null) return false;

        PacketDistributor.sendToServer(new ColonyCoreMovePacket(sourcePos, ghostPos, colonyId));

        exit();
        return true;
    }

    // ESC 取消：退出迁移模式
    public static boolean onCancel() {
        if (!active) return false;
        exit();
        return true;
    }
}
