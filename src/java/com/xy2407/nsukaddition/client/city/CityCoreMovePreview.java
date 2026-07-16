package com.xy2407.nsukaddition.client.city;

import com.xy2407.nsukaddition.common.network.ModNetwork;
import com.xy2407.nsukaddition.common.network.city.CityCoreMovePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

/** 城市核心迁移客户端状态管理：预览模式开关、ghost位置、右键拦截、Enter确认。 */
@OnlyIn(Dist.CLIENT)
public final class CityCoreMovePreview {

    private static boolean active = false;

    private static BlockPos ghostPos = null;

    private static BlockPos oldCorePos = null;

    private static UUID cityId = null;

    private CityCoreMovePreview() {}

    public static boolean isActive() { return active; }
    public static BlockPos getGhostPos() { return ghostPos; }

    public static void enter(BlockPos corePos, UUID id) {
        active = true;
        ghostPos = null;
        oldCorePos = corePos.immutable();
        cityId = id;
    }

    public static void exit() {
        active = false;
        ghostPos = null;
        oldCorePos = null;
        cityId = null;
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

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new CityCoreMovePacket(oldCorePos, ghostPos, cityId));

        exit();
        return true;
    }

    public static boolean onCancel() {
        if (!active) return false;
        exit();
        return true;
    }
}
