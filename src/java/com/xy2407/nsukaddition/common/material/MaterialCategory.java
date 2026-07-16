package com.xy2407.nsukaddition.common.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** 材料分类定义，按物品 ID 集合判断方块或物品是否属于该分类。 */
public final class MaterialCategory {

    private final String key;
    private final String displayName;
    private final Set<String> itemIds;
    private final Set<String> itemIdsView;

    public MaterialCategory(String key, String displayName, Set<String> itemIds) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        Set<String> normalized = new LinkedHashSet<>();
        if (itemIds != null) {
            for (String id : itemIds) {
                String trimmed = id == null ? null : id.trim().toLowerCase(java.util.Locale.ROOT);
                if (trimmed != null && !trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        this.itemIds = normalized;
        this.itemIdsView = Collections.unmodifiableSet(this.itemIds);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Set<String> itemIds() {
        return itemIdsView;
    }

    public boolean contains(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return itemIds.contains(itemId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean containsBlock(Block block) {
        if (block == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && contains(id.toString());
    }

    public boolean containsStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && contains(id.toString());
    }
}
