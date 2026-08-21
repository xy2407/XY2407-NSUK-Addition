package com.xy2407.nsukaddition.common.capture;

import common.cn.kafei.simukraft.material.GenericContainerAccess;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/** 容器槽位写入与仓库内同类捕获器合并工具，供外贸/自由市场交付时把生物并入既有捕获器。 */
public final class CaptureContainerUtil {

    private CaptureContainerUtil() {
    }

    public static void writeSlot(ServerLevel level, BlockPos pos, GenericContainerAccess.SlotAccess access,
                                 @Nullable Direction side, int slot, ItemStack stack) {
        if (level == null || pos == null || stack == null || !level.isLoaded(pos)) {
            return;
        }
        try {
            if (access == GenericContainerAccess.SlotAccess.ITEM_HANDLER) {
                IItemHandler handler = side != null
                        ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)
                        : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, (Direction) null);
                if (handler == null || slot < 0 || slot >= handler.getSlots()) {
                    return;
                }
                ItemStack current = handler.getStackInSlot(slot);
                if (!current.isEmpty()) {
                    handler.extractItem(slot, current.getCount(), false);
                }
                handler.insertItem(slot, stack, false);
                return;
            }
            Container container = resolveContainer(level, pos);
            if (container == null || slot < 0 || slot >= container.getContainerSize()) {
                return;
            }
            container.setItem(slot, stack);
            container.setChanged();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static ItemStack mergeIntoWarehouses(ServerLevel level, List<BlockPos> warehouses, ItemStack device) {
        if (device == null || device.isEmpty() || warehouses == null) {
            return device;
        }
        EntityType<?> type = EntityCaptureItem.getEntityType(device);
        if (type == null) {
            return device;
        }
        boolean baby = EntityCaptureItem.isBaby(device);
        for (BlockPos warehouse : warehouses) {
            if (device.isEmpty() || EntityCaptureItem.getEntryCount(device) <= 0) {
                break;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, warehouse)) {
                ItemStack slotStack = snapshot.stack();
                if (!EntityCaptureItem.isCompatibleCapture(slotStack, type)) {
                    continue;
                }
                int space = EntityCaptureItem.MAX_CAPTURES - EntityCaptureItem.getEntryCount(slotStack);
                int toMove = Math.min(space, EntityCaptureItem.getEntryCount(device));
                if (toMove <= 0) {
                    continue;
                }
                ItemStack modified = slotStack.copy();
                EntityCaptureItem.fillEntries(modified, toMove, baby);
                writeSlot(level, warehouse, snapshot.access(), snapshot.side(), snapshot.slot(), modified);
                EntityCaptureItem.removeEntries(device, toMove);
            }
        }
        return device;
    }

    @Nullable
    private static Container resolveContainer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, true);
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }
}