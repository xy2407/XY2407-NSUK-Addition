package com.xy2407.nsukaddition.common.compat.vinerykaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BottleBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.init.tag.TagMod;
import com.github.ysbbbbbb.kaleidoscopetavern.item.BottleBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.vinery.core.block.WineBottleBlock;
import net.satisfy.vinery.core.block.entity.StorageBlockEntity;
import net.satisfy.vinery.core.item.DrinkBlockItem;
import org.jetbrains.annotations.Nullable;

/** Vinery 与 Kaleidoscope Tavern 双向兼容辅助类，提供物品识别与方块状态构造。 */
public final class VineryKaleidoscopeCompat {

    private VineryKaleidoscopeCompat() {
    }

    public static boolean isKaleidoscopeBottle(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof BottleBlockItem && ((BottleBlockItem) item).getBlock() instanceof BottleBlock;
    }

    public static boolean isIrregularKaleidoscopeBottle(ItemStack stack) {
        return isKaleidoscopeBottle(stack) && stack.is(TagMod.BAR_CABINET_IRREGULAR);
    }

    public static boolean isRegularKaleidoscopeBottle(ItemStack stack) {
        return isKaleidoscopeBottle(stack) && !stack.is(TagMod.BAR_CABINET_IRREGULAR);
    }

    public static boolean isVineryBottle(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof DrinkBlockItem && ((DrinkBlockItem) item).getBlock() instanceof WineBottleBlock;
    }

    public static boolean isVineryBottleItem(Item item) {
        return item instanceof DrinkBlockItem && ((DrinkBlockItem) item).getBlock() instanceof WineBottleBlock;
    }

    public static boolean isAnyBottle(ItemStack stack) {
        return isKaleidoscopeBottle(stack) || isVineryBottle(stack);
    }

    @Nullable
    public static BlockState getVineryRenderState(ItemStack stack) {
        if (!isVineryBottle(stack)) {
            return null;
        }
        DrinkBlockItem item = (DrinkBlockItem) stack.getItem();
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(WineBottleBlock.FAKE_MODEL)) {
            state = state.setValue(WineBottleBlock.FAKE_MODEL, false);
        }
        return state;
    }

    public static boolean placeVineryBottleResult(Level level, BlockPos pos, ItemStack resultStack) {
        if (!isVineryBottle(resultStack)) {
            return false;
        }
        DrinkBlockItem item = (DrinkBlockItem) resultStack.getItem();
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(WineBottleBlock.FAKE_MODEL)) {
            state = state.setValue(WineBottleBlock.FAKE_MODEL, false);
        }
        level.setBlockAndUpdate(pos, state);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof StorageBlockEntity storageEntity) {
            storageEntity.setStack(0, resultStack.copyWithCount(1));
        }
        return true;
    }
}
