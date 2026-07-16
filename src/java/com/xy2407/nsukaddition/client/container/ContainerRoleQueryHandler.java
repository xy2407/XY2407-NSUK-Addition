package com.xy2407.nsukaddition.client.container;

import com.xy2407.nsukaddition.common.network.ContainerRoleQueryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;

/** 容器角色查询处理器，每 N tick 进行射线检测并向服务端查询容器角色。 */
public final class ContainerRoleQueryHandler {

    private static final int RAY_TRACE_INTERVAL_TICKS = 5;
    private static int tickCounter;

    private ContainerRoleQueryHandler() {}

    public static void onClientTick() {
        tickCounter++;
        if (tickCounter < RAY_TRACE_INTERVAL_TICKS) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            ContainerRoleClientCache.clearAll();
            return;
        }

        BlockPos hitPos = ((BlockHitResult) hit).getBlockPos();
        BlockPos lastPos = ContainerRoleClientCache.lastQueriedPos();

        if (hitPos.equals(lastPos)) return;

        BlockEntity be = mc.level.getBlockEntity(hitPos);
        if (be == null) {
            ContainerRoleClientCache.clearAll();
            return;
        }

        if (!(be instanceof ChestBlockEntity || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity)) {
            var cap = mc.level.getCapability(Capabilities.ItemHandler.BLOCK, hitPos, null);
            if (cap == null) {
                ContainerRoleClientCache.clearAll();
                return;
            }
        }

        ContainerRoleClientCache.setLastQueriedPos(hitPos);
        ContainerRoleClientCache.clearResponse();

        PacketDistributor.sendToServer(new ContainerRoleQueryPacket(hitPos));
    }
}
