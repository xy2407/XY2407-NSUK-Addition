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

    private static boolean active = false;

    private static BlockPos ghostPos = null;

    private static BlockPos sourcePos = null;

    private static UUID colonyId = null;

    private ColonyCoreMovePreview() {}

    public static boolean isActive() { return active; }
    public static BlockPos getGhostPos() { return ghostPos; }

    public static void enter(BlockPos pos, UUID id) {
        active = true;
        ghostPos = null;
        sourcePos = pos.immutable();
        colonyId = id;
    }

    public static void exit() {
        active = false;
        ghostPos = null;
        sourcePos = null;
        colonyId = null;
    }

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

    public static boolean onConfirm() {
        if (!active || ghostPos == null) return false;

        PacketDistributor.sendToServer(new ColonyCoreMovePacket(sourcePos, ghostPos, colonyId));

        exit();
        return true;
    }

    public static boolean onCancel() {
        if (!active) return false;
        exit();
        return true;
    }
}
