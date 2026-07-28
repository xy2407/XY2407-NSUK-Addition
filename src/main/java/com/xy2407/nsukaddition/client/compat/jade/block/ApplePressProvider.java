package com.xy2407.nsukaddition.client.compat.jade.block;

import com.xy2407.nsukaddition.client.compat.jade.NsukJadePlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.vinery.core.block.entity.ApplePressBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade 提供者：Vinery ApplePress，布局完全匹配 Kaleidoscope BarrelComponentProvider 风格。 */
public enum ApplePressProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof ApplePressBlockEntity press)) {
            return;
        }

        ItemStack mashInput = press.getItem(0);
        ItemStack mashOutput = press.getItem(1);
        ItemStack bottle = press.getItem(2);
        ItemStack fermentOutput = press.getItem(3);

        if (mashInput.isEmpty() && mashOutput.isEmpty() && fermentOutput.isEmpty()) {
            tooltip.add(Component.translatable("message.kaleidoscope_tavern.barrel.not_brewing"));
            return;
        }

        if (!fermentOutput.isEmpty()) {
            Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                    fermentOutput.getHoverName(), fermentOutput.getCount());
            tooltip.add(resultText);
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.apple_press.complete")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }

        if (!mashOutput.isEmpty() && !bottle.isEmpty()) {
            Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                    mashOutput.getHoverName(), mashOutput.getCount());
            tooltip.add(resultText);
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.apple_press.fermenting"));
            return;
        }

        if (!mashInput.isEmpty()) {
            Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                    mashInput.getHoverName(), mashInput.getCount());
            tooltip.add(resultText);
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.apple_press.mashing"));
            return;
        }

        if (!mashOutput.isEmpty() && bottle.isEmpty()) {
            Component resultText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                    mashOutput.getHoverName(), mashOutput.getCount());
            tooltip.add(resultText);
            tooltip.add(Component.translatable("jade.plugin_xy2407_nsuk_addition.apple_press.waiting_bottle"));
        }
    }

    @Override
    public net.minecraft.resources.ResourceLocation getUid() {
        return NsukJadePlugin.APPLE_PRESS;
    }
}
