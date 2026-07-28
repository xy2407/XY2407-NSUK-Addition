package com.xy2407.nsukaddition.client.compat.jade.block;

import com.xy2407.nsukaddition.client.compat.jade.NsukJadePlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.vinery.core.block.WineBottleBlock;
import net.satisfy.vinery.core.block.entity.StorageBlockEntity;
import net.minecraft.core.NonNullList;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade 提供者：Vinery WineBottleBlock，布局完全匹配 Kaleidoscope BarrelComponentProvider 风格。 */
public enum WineBottleProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof StorageBlockEntity storage)) {
            return;
        }

        NonNullList<ItemStack> inventory = storage.getInventory();
        int filled = 0;
        ItemStack firstItem = ItemStack.EMPTY;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                if (firstItem.isEmpty()) firstItem = stack;
                filled++;
            }
        }

        if (filled == 0) {
            tooltip.add(Component.translatable("message.kaleidoscope_tavern.barrel.not_brewing"));
            return;
        }

        int maxCount = accessor.getBlockState().getBlock() instanceof WineBottleBlock wine ? wine.size() : inventory.size();

        if (filled == 1) {
            Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                    firstItem.getHoverName(), firstItem.getCount());
            tooltip.add(resultText);
        } else {
            Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                    firstItem.getHoverName(), filled);
            tooltip.add(resultText);
        }

        if (filled >= maxCount) {
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.wine_bottle.count", filled, maxCount)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.wine_bottle.count", filled, maxCount));
        }
    }

    @Override
    public net.minecraft.resources.ResourceLocation getUid() {
        return NsukJadePlugin.WINE_BOTTLE;
    }
}
