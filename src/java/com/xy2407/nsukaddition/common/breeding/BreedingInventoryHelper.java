package com.xy2407.nsukaddition.common.breeding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** 繁殖系统物品操作，提供容器物品查询、消耗与存入。 */
@SuppressWarnings("null")
public final class BreedingInventoryHelper {

    private BreedingInventoryHelper() {
    }

    public static boolean hasItem(ServerLevel level, List<BlockPos> positions, String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return true;
        }
        int needed = count;
        for (BlockPos pos : positions) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            needed -= countItem(container, itemId);
            if (needed <= 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean consumeItem(ServerLevel level, List<BlockPos> positions, String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return true;
        }
        int remaining = count;
        for (BlockPos pos : positions) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            remaining -= removeItem(container, itemId, remaining);
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasItems(ServerLevel level, List<BlockPos> positions, List<BreedingDefinition.InputItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        for (BreedingDefinition.InputItem item : items) {
            if (item == null) {
                continue;
            }
            if (!hasItem(level, positions, item.itemId(), item.count())) {
                return false;
            }
        }
        return true;
    }

    public static boolean consumeItems(ServerLevel level, List<BlockPos> positions, List<BreedingDefinition.InputItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        if (!hasItems(level, positions, items)) {
            return false;
        }
        for (BreedingDefinition.InputItem item : items) {
            if (item == null) {
                continue;
            }
            if (!consumeItem(level, positions, item.itemId(), item.count())) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasFood(ServerLevel level, List<BlockPos> positions, Animal animal, int count) {
        if (animal == null || count <= 0) {
            return true;
        }
        int found = 0;
        for (BlockPos pos : positions) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && animal.isFood(stack)) {
                    found += stack.getCount();
                    if (found >= count) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean consumeFood(ServerLevel level, List<BlockPos> positions, Animal animal, int count) {
        if (animal == null || count <= 0) {
            return true;
        }
        int remaining = count;
        for (BlockPos pos : positions) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && animal.isFood(stack)) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.shrink(take);
                    remaining -= take;
                    container.setChanged();
                }
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    public static boolean depositItem(ServerLevel level, List<BlockPos> positions, String itemId, int count) {
        if (count <= 0 || itemId == null || itemId.isBlank()) {
            return true;
        }
        ItemStack stack = createStack(itemId, count);
        if (stack.isEmpty()) {
            return false;
        }
        for (BlockPos pos : positions) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            stack = insertStack(container, stack);
            if (stack.isEmpty()) {
                return true;
            }
        }
        if (!stack.isEmpty()) {
            BlockPos dropPos = positions.isEmpty() ? BlockPos.ZERO : positions.getFirst();
            ItemEntity entity = new ItemEntity(level, dropPos.getX() + 0.5, dropPos.getY() + 1.0, dropPos.getZ() + 0.5, stack);
            level.addFreshEntity(entity);
        }
        return true;
    }

    public static ItemStack depositItemStack(ServerLevel level, List<BlockPos> positions, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        for (BlockPos pos : positions) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            remaining = insertStack(container, remaining);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    static Container containerAt(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    private static int countItem(Container container, String itemId) {
        int count = 0;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return 0;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int removeItem(Container container, String itemId, int max) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || max <= 0) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return 0;
        }
        int removed = 0;
        for (int i = 0; i < container.getContainerSize() && removed < max; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                int take = Math.min(stack.getCount(), max - removed);
                stack.shrink(take);
                removed += take;
                container.setChanged();
            }
        }
        return removed;
    }

    private static ItemStack insertStack(Container container, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                int place = Math.min(remaining.getCount(), container.getMaxStackSize());
                container.setItem(i, remaining.split(place));
                container.setChanged();
                continue;
            }
            if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                int room = Math.min(container.getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount();
                if (room <= 0) {
                    continue;
                }
                int move = Math.min(room, remaining.getCount());
                slot.grow(move);
                remaining.shrink(move);
                container.setChanged();
            }
        }
        return remaining;
    }

    private static ItemStack createStack(String itemId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, count));
    }
}
