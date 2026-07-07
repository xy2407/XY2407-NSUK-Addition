package com.xy2407.nsukaddition.common.block.entity;

import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** 矿业控制箱方块实体，管理镐子物品栏与耐久消耗。 */
@SuppressWarnings("null")
public final class MiningControlBoxBlockEntity extends BlockEntity {

    public static final int PICKAXE_SLOTS = 4;

    private final ItemStackHandler pickaxes = new ItemStackHandler(PICKAXE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || stack.getItem() instanceof PickaxeItem;
        }
    };

    public MiningControlBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MINING_CONTROL_BOX_ENTITY.get(), pos, state);
    }

    public ItemStackHandler pickaxes() {
        return pickaxes;
    }

    public boolean hasPickaxe() {
        for (int i = 0; i < PICKAXE_SLOTS; i++) {
            ItemStack stack = pickaxes.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Pickaxes", pickaxes.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Pickaxes")) {
            pickaxes.deserializeNBT(registries, tag.getCompound("Pickaxes"));
        }
    }

    public void setPickaxe(int slot, ItemStack stack) {
        if (slot >= 0 && slot < PICKAXE_SLOTS) {
            pickaxes.setStackInSlot(slot, stack);
            setChanged();
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < PICKAXE_SLOTS; i++) {
            ItemStack stack = pickaxes.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            level.addFreshEntity(new ItemEntity(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
            pickaxes.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public void applyPickaxeDurability(int blocksMined) {
        if (blocksMined <= 0 || level == null) return;
        int remaining = blocksMined;

        for (int slot = 0; slot < PICKAXE_SLOTS && remaining > 0; slot++) {
            ItemStack stack = pickaxes.getStackInSlot(slot);
            if (!isValidPickaxe(stack)) continue;

            int unbreaking = stack.getEnchantmentLevel(level.registryAccess().holderOrThrow(Enchantments.UNBREAKING));
            boolean broken = false;
            while (remaining > 0 && !stack.isEmpty()) {
                remaining--;
                if (shouldConsumeDurability(unbreaking)) {
                    stack.setDamageValue(stack.getDamageValue() + 1);
                    if (stack.getDamageValue() >= stack.getMaxDamage()) {
                        pickaxes.setStackInSlot(slot, ItemStack.EMPTY);
                        broken = true;
                        break;
                    }
                }
            }

            if (!broken && !stack.isEmpty()) {
                pickaxes.setStackInSlot(slot, stack);
            }
        }
        setChanged();
    }

    private boolean isValidPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PickaxeItem
                && stack.getDamageValue() < stack.getMaxDamage();
    }

    private boolean shouldConsumeDurability(int unbreakingLevel) {
        if (unbreakingLevel <= 0) return true;

        return level.random.nextInt(unbreakingLevel + 1) == 0;
    }
}
