package com.xy2407.nsukaddition.client.farmland;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/** 在 UIElement 上渲染作物种子的物品图标（16x16），用于农田盒作物选择界面。 */
public class CropIconElement extends UIElement {
    private final ItemStack stack;

    public CropIconElement(ItemStack stack) {
        this.stack = stack;
        layout(layout -> {
            layout.width(16);
            layout.height(16);
        });
    }

    @Override
    public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        guiContext.graphics.renderItem(stack, x, y);
    }
}
