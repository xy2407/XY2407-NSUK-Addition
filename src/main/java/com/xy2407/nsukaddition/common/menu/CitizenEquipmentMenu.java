package com.xy2407.nsukaddition.common.menu;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** 市民装备容器菜单，包含 4 个盔甲槽和玩家背包槽。 */
@SuppressWarnings("null")
public final class CitizenEquipmentMenu extends AbstractContainerMenu {

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int[] ARMOR_SLOT_X = {8, 8, 8, 8};
    private static final int[] ARMOR_SLOT_Y = {26, 44, 62, 80};

    private final UUID citizenUuid;
    private final int citizenId;
    private final CitizenEntity citizen;

    public CitizenEquipmentMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, resolveCitizen(inventory.player.level(), buffer),
                buffer.readInt());
    }

    public CitizenEquipmentMenu(int containerId, Inventory inventory, CitizenEntity citizen) {
        this(containerId, inventory, citizen, citizen != null ? citizen.getId() : -1);
    }

    private CitizenEquipmentMenu(int containerId, Inventory inventory, CitizenEntity citizen, int citizenId) {
        super(ModMenuTypes.CITIZEN_EQUIPMENT.get(), containerId);
        this.citizen = citizen;
        this.citizenUuid = citizen != null ? citizen.getUUID() : null;
        this.citizenId = citizenId;

        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            addSlot(new CitizenArmorSlot(citizen, ARMOR_SLOTS[i], ARMOR_SLOT_X[i], ARMOR_SLOT_Y[i]));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 168));
        }
    }

    private static CitizenEntity resolveCitizen(net.minecraft.world.level.Level level, RegistryFriendlyByteBuf buffer) {
        if (level == null || buffer == null) return null;
        UUID uuid = buffer.readUUID();
        if (level instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
            return entity instanceof CitizenEntity citizen ? citizen : null;
        }
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (citizen == null || citizen.level().isClientSide()) return player.isAlive();
        return !citizen.isRemoved() && citizen.isAlive() && player.distanceToSqr(citizen) <= 64.0D;
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (citizen == null || citizen.level().isClientSide()) return;
        if (citizen.level() instanceof ServerLevel level) {
            com.xy2407.nsukaddition.common.citizen.CitizenEquipmentService.saveFromEntity(level, citizen);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < ARMOR_SLOTS.length) {
            if (!moveItemStackTo(stack, ARMOR_SLOTS.length, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, ARMOR_SLOTS.length, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    public UUID citizenUuid() {
        return citizenUuid;
    }

    public int citizenId() {
        return citizenId;
    }

    public CitizenEntity citizen() {
        return citizen;
    }

    private static final class CitizenArmorSlot extends Slot {

        private final CitizenEntity citizen;
        private final EquipmentSlot slot;
        private ItemStack displayStack = ItemStack.EMPTY;

        CitizenArmorSlot(CitizenEntity citizen, EquipmentSlot slot, int x, int y) {
            super(new net.minecraft.world.SimpleContainer(1), 0, x, y);
            this.citizen = citizen;
            this.slot = slot;
        }

        @Override
        public ItemStack getItem() {
            if (citizen != null) return citizen.getItemBySlot(slot);
            return displayStack;
        }

        @Override
        public void set(ItemStack stack) {
            displayStack = stack.copy();
            if (citizen != null) {
                citizen.setItemSlot(slot, stack);
            }
            setChanged();
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (citizen == null || citizen.level().isClientSide()) return;
            if (citizen.level() instanceof ServerLevel level) {
                com.xy2407.nsukaddition.common.citizen.CitizenEquipmentService.saveFromEntity(level, citizen);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            if (stack.getItem() instanceof ArmorItem armor) {
                return armor.getEquipmentSlot() == slot;
            }
            return false;
        }
    }
}
