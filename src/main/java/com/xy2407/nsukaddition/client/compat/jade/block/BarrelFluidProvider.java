package com.xy2407.nsukaddition.client.compat.jade.block;

import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity;
import com.xy2407.nsukaddition.client.compat.jade.NsukJadePlugin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade 提供者：在 Kaleidoscope 酒桶上追加显示 Vinery 葡萄汁瓶物品信息（非流体桶），布局匹配 BarrelComponentProvider 风格。 */
public enum BarrelFluidProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final String[][] FLUID_TO_JUICE_ITEM = {
            {"red_grape_juice", "red_grapejuice"},
            {"red_savanna_grape_juice", "red_savanna_grapejuice"},
            {"red_taiga_grape_juice", "red_taiga_grapejuice"},
            {"red_jungle_grape_juice", "red_jungle_grapejuice"},
            {"white_grape_juice", "white_grapejuice"},
            {"white_savanna_grape_juice", "white_savanna_grapejuice"},
            {"white_taiga_grape_juice", "white_taiga_grapejuice"},
            {"white_jungle_grape_juice", "white_jungle_grapejuice"},
            {"apple_juice", "apple_juice"},
    };

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BarrelBlockEntity be = BarrelBlock.getBarrelEntity(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
        if (be == null || !be.isBrewing()) {
            return;
        }

        FluidStack fluidStack = be.getFluid().getFluid();
        if (fluidStack.isEmpty()) {
            return;
        }

        Fluid fluid = fluidStack.getFluid();
        String fluidPath = BuiltInRegistries.FLUID.getKey(fluid).getPath();

        Item juiceItem = null;
        for (String[] mapping : FLUID_TO_JUICE_ITEM) {
            if (mapping[0].equals(fluidPath)) {
                juiceItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vinery", mapping[1]));
                break;
            }
        }
        if (juiceItem == null || juiceItem == net.minecraft.world.item.Items.AIR) {
            return;
        }

        ItemStack juiceStack = new ItemStack(juiceItem);
        int amount = fluidStack.getAmount() / 1000;
        Component fluidText = Component.translatable("jade.plugin_kaleidoscope_tavern.item_and_count",
                juiceStack.getHoverName(), amount);
        tooltip.add(fluidText);
    }

    @Override
    public ResourceLocation getUid() {
        return NsukJadePlugin.BARREL_FLUID;
    }
}
