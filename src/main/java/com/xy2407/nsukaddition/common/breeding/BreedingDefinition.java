package com.xy2407.nsukaddition.common.breeding;

import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 繁殖定义，描述建筑内繁殖系统的点位、容器与配方配置。 */
@SuppressWarnings("null")
public record BreedingDefinition(String id,
                                 String name,
                                 String jobType,
                                 String jobName,
                                 String heldItem,
                                 Map<String, PointDefinition> points,
                                 Map<String, ContainerDefinition> containers,
                                 List<RecipeDefinition> recipes,
                                 Path sourcePath) {

    public RecipeDefinition recipeById(String recipeId) {
        if (recipeId != null && !recipeId.isBlank()) {
            for (RecipeDefinition recipe : recipes) {
                if (recipe.id().equals(recipeId)) {
                    return recipe;
                }
            }
        }
        return recipes.isEmpty() ? null : recipes.getFirst();
    }

    public String defaultRecipeId() {
        return recipes.isEmpty() ? "" : recipes.getFirst().id();
    }

    public record PointDefinition(String id, String type, List<BlockPos> positions) {
    }

    public record ContainerDefinition(String id, String type, List<BlockPos> positions) {
    }

    public record RecipeDefinition(String id,
                                   String name,
                                   RecipeType type,
                                   String entityType,
                                   String feedItem,
                                   String heldItem) {
        public String effectiveHeldItem(String fallback) {
            return heldItem != null && !heldItem.isBlank() ? heldItem : fallback;
        }

        public String targetEntityType() {
            return entityType != null ? entityType : "";
        }

        public String effectiveFeedItem() {
            return feedItem != null ? feedItem : "";
        }

        public int maxEntities() {
            return type == RecipeType.BREEDING_COLLECT ? 12 : 6;
        }

        public List<InputItem> inputItems() {
            String itemId = effectiveFeedItem();
            if (itemId.isBlank()) {
                return List.of();
            }
            return List.of(new InputItem(itemId, 1));
        }

        public boolean requireFood() {
            return !effectiveFeedItem().isBlank();
        }

        public OutputItem outputItem() {
            return null;
        }

        public String outputEntity() {
            return "";
        }

        public int cooldownTicks() {
            return 0;
        }

        public int breedCount() {
            return 1;
        }
    }

    public enum RecipeType {
        BREEDING_SLAUGHTER,
        BREEDING_COLLECT;

        public static RecipeType fromString(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            return switch (normalized) {
                case "breeding_collect", "collect", "collect_breeding", "繁殖采集配方", "采集", "采集配方" -> BREEDING_COLLECT;
                default -> BREEDING_SLAUGHTER;
            };
        }
    }

    public record InputItem(String itemId, int count) {
        public InputItem {
            itemId = itemId != null ? itemId : "";
            count = Math.max(1, count);
        }
    }

    public record OutputItem(String itemId, int count) {
        public OutputItem {
            itemId = itemId != null ? itemId : "";
            count = Math.max(1, count);
        }
    }
}
