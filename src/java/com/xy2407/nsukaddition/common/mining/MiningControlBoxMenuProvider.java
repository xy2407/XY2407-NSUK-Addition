package com.xy2407.nsukaddition.common.mining;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.xy2407.nsukaddition.common.menu.ModMenuTypes;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 挖矿控制盒的菜单提供者，负责服务端打开菜单与客户端菜单创建。 */
@SuppressWarnings("null")
public final class MiningControlBoxMenuProvider implements MenuProvider {

    private final BlockPos boxPos;

    private MiningControlBoxMenuProvider(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
    }

    public static boolean open(ServerPlayer player, BlockPos boxPos) {
        MiningControlBoxView view = MiningControlBoxService.buildView(player.serverLevel(), boxPos);
        MiningControlBoxOpenResponsePacket packet = MiningControlBoxOpenResponsePacket.from(view);
        return player.openMenu(new MiningControlBoxMenuProvider(boxPos),
                buffer -> MiningControlBoxOpenResponsePacket.encode(buffer, packet)).isPresent();
    }

    public static ModularUIContainerMenu createClientMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        MiningControlBoxOpenResponsePacket packet = buffer != null ? MiningControlBoxOpenResponsePacket.decode(buffer) : emptyPacket();
        return new ModularUIContainerMenu(ModMenuTypes.MINING_CONTROL_BOX.get(),
                containerId, inventory, new MiningControlBoxMenuHolder(packet));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        MiningControlBoxView view = MiningControlBoxService.buildView((net.minecraft.server.level.ServerLevel) player.level(), boxPos);
        MiningControlBoxOpenResponsePacket packet = MiningControlBoxOpenResponsePacket.from(view);
        return new ModularUIContainerMenu(ModMenuTypes.MINING_CONTROL_BOX.get(),
                containerId, inventory, new MiningControlBoxMenuHolder(packet));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.xy2407_nsuk_addition.mining.title");
    }

    private static MiningControlBoxOpenResponsePacket emptyPacket() {
        return new MiningControlBoxOpenResponsePacket(BlockPos.ZERO, false, "", 0, false, 0, 0, "", "");
    }
}
