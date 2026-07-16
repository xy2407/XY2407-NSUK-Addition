package com.xy2407.nsukaddition.common.material;

import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 根据配置的分组规则扩展建材类别成员的工具类。 */
public final class MaterialCategoryExpander {

    private MaterialCategoryExpander() {
    }

    public static void expandGroupMembers(String blockId, LinkedHashSet<Item> acceptedItems) {
        if (blockId == null || blockId.isBlank()) {
            return;
        }
        if (ServerConfig.materialsCreativeMode()
                || ServerConfig.materialsExpertMode()
                || !ServerConfig.materialCategoryMatchingEnabled()) {
            return;
        }

        MaterialGroup group = findGroup(blockId);
        if (group == null || group.headers().isEmpty()) {
            return;
        }

        String normalizedBlockId = blockId.trim().toLowerCase(Locale.ROOT);

        if (group.isHeader(blockId)) {

            for (String memberId : group.members()) {
                resolveItem(memberId).ifPresent(acceptedItems::add);
            }
        } else {

            for (String memberId : group.members()) {
                if (memberId.equals(normalizedBlockId)) {
                    continue;
                }
                resolveItem(memberId).ifPresent(acceptedItems::add);
            }
        }
    }

    private record MaterialGroup(List<String> headers, List<String> members) {
        boolean isHeader(String id) {
            return headers.contains(id.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static MaterialGroup findGroup(String blockId) {
        String normalized = blockId.trim().toLowerCase(Locale.ROOT);
        for (String entry : ServerConfig.materialCategoryGroups()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split("\\|", 3);
            if (parts.length < 2) {
                continue;
            }
            List<String> headers = splitIds(parts[1]);
            List<String> members = parts.length >= 3 ? splitIds(parts[2]) : List.of();
            if (headers.contains(normalized) || members.contains(normalized)) {
                return new MaterialGroup(headers, members);
            }
        }
        return null;
    }

    private static List<String> splitIds(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String raw : value.split(",")) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (!id.isBlank()) {
                result.add(id);
            }
        }
        return result;
    }

    private static Optional<Item> resolveItem(String materialId) {
        ResourceLocation id = ResourceLocation.tryParse(materialId);
        if (id == null) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != null && item != Items.AIR) {
            return Optional.of(item);
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block != null) {
            Item blockItem = block.asItem();
            if (blockItem != null && blockItem != Items.AIR) {
                return Optional.of(blockItem);
            }
        }
        return Optional.empty();
    }
}
