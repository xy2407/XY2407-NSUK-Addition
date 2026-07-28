package com.xy2407.nsukaddition.client.compat.jade.block;

import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.deco.StorageBlockEntity;
import com.xy2407.nsukaddition.client.compat.jade.NsukJadePlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade 提供者：Kaleidoscope 存储架，布局完全匹配 BarrelComponentProvider 风格。 */
public enum StorageRackProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof StorageBlockEntity storage)) {
            return;
        }

        ItemStackHandler items = storage.getItems();
        int filled = 0;
        ItemStack firstItem = ItemStack.EMPTY;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (firstItem.isEmpty()) firstItem = stack;
                filled++;
            }
        }

        if (filled == 0) {
            tooltip.add(Component.translatable("message.kaleidoscope_tavern.barrel.not_brewing"));
            return;
        }

        Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                firstItem.getHoverName(), filled);
        tooltip.add(resultText);

        int total = items.getSlots();
        if (filled >= total) {
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.storage_rack.count", filled, total)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.storage_rack.count", filled, total));
        }
    }

    @Override
    public net.minecraft.resources.ResourceLocation getUid() {
        return NsukJadePlugin.STORAGE_RACK;
    }
}
