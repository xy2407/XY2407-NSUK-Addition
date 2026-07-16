package com.xy2407.nsukaddition.common.menu;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.UUID;

/** 市民装备菜单提供者，负责服务端打开菜单与向客户端同步目标市民 UUID。 */
@SuppressWarnings("null")
public final class CitizenEquipmentMenuProvider implements MenuProvider {

    private final UUID citizenUuid;

    private CitizenEquipmentMenuProvider(UUID citizenUuid) {
        this.citizenUuid = citizenUuid;
    }

    public static boolean open(ServerPlayer player, CitizenEntity citizen) {
        if (citizen == null || citizen.isRemoved()) return false;
        return player.openMenu(new CitizenEquipmentMenuProvider(citizen.getUUID()),
                buffer -> {
                    buffer.writeUUID(citizen.getUUID());
                    buffer.writeInt(citizen.getId());
                }).isPresent();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        CitizenEntity citizen = null;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.world.entity.Entity entity = level.getEntity(citizenUuid);
            if (entity instanceof CitizenEntity c) {
                citizen = c;
            }
        }
        return new CitizenEquipmentMenu(containerId, inventory, citizen);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.xy2407_nsuk_addition.citizen_equipment.title");
    }
}
