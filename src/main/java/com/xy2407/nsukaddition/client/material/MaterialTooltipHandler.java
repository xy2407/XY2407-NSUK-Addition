package com.xy2407.nsukaddition.client.material;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Set;

/** 为 12 种建筑材料分类内的物品追加 tooltip，标注其所属材料分类，方便玩家分区归类。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID, value = Dist.CLIENT)
public final class MaterialTooltipHandler {

    private static final Set<String> CATEGORY_KEYS = Set.of(
            "wood", "stone", "brick", "sand", "prismarine", "quartz",
            "wool", "terracotta", "concrete", "glass", "leaves", "lighting");

    private MaterialTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String key = MaterialCategoryRegistry.getCategoryKey(stack);
        if (key == null || !CATEGORY_KEYS.contains(key)) {
            return;
        }
        MaterialCategory category = MaterialCategoryRegistry.get(key);
        if (category == null) {
            return;
        }
        Component line = Component.translatable(
                "tooltip.xy2407_nsuk_addition.material_category", category.displayName())
                .withStyle(style -> style.withColor(0x55FF55));
        event.getToolTip().add(line);
    }
}