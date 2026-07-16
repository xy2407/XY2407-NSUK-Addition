package com.xy2407.nsukaddition.common.mining;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.xy2407.nsukaddition.common.network.clientbound.MiningControlBoxUiBridge;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenResponsePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 挖矿控制盒容器 UI 持有者，根据侧别创建客户端或服务端界面。 */
@SuppressWarnings("null")
public final class MiningControlBoxMenuHolder implements IContainerUIHolder {

    private final MiningControlBoxOpenResponsePacket packet;

    public MiningControlBoxMenuHolder(MiningControlBoxOpenResponsePacket packet) {
        this.packet = packet;
    }

    @Override
    public ModularUI createUI(Player player) {
        if (player != null && player.level().isClientSide()) {
            return MiningControlBoxUiBridge.createClientUI(player, packet);
        }
        return ModularUI.of(UI.of(new MiningControlBoxMenuSlotsRoot(player, packet)), player);
    }

    @Override
    public boolean isStillValid(Player player) {
        if (player == null || player.level().isClientSide()) {
            return true;
        }
        if (packet.boxPos() == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel level) {
            return serverPlayer.blockPosition().closerThan(packet.boxPos(), 16.0D);
        }
        return false;
    }
}
