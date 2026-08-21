package com.xy2407.nsukaddition.common.cooking;

import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 餐厅定义，包含名称、职业、容器、座位、可选菜品列表及价格。 */
@SuppressWarnings("null")
public record RestaurantDefinition(String id,
                                   String name,
                                   JobDefinition job,
                                   Map<String, PointDefinition> points,
                                   Map<String, ContainerDefinition> containers,
                                   List<SeatDefinition> seats,
                                   List<String> cook,
                                   Map<String, Double> cookPrices,
                                   List<RecipeDefinition> recipes,
                                   List<BlockPos> outputBlock,
                                   Path sourcePath,
                                   String waiterType) {
    public RestaurantDefinition {
        id = id != null && !id.isBlank() ? id.trim() : "restaurant";
        name = name != null && !name.isBlank() ? name.trim() : id;
        job = job != null ? job : new JobDefinition("chef", "厨师", "");
        points = points != null ? Map.copyOf(points) : Map.of();
        containers = containers != null ? Map.copyOf(containers) : Map.of();
        seats = seats != null ? List.copyOf(seats) : List.of();
        cook = cook != null ? List.copyOf(cook) : List.of();
        cookPrices = cookPrices != null ? Map.copyOf(cookPrices) : Map.of();
        recipes = recipes != null ? List.copyOf(recipes) : List.of();
        outputBlock = outputBlock != null ? List.copyOf(outputBlock) : List.of();
        String normalizedWaiter = waiterType == null ? "" : waiterType.trim().toLowerCase(Locale.ROOT);
        waiterType = switch (normalizedWaiter) {
            case "maid" -> "maid";
            case "and" -> "and";
            default -> "nsuk";
        };
    }

    public boolean isMaidWaiter() {
        return "maid".equals(waiterType) || "and".equals(waiterType);
    }

    public boolean isNsukWaiter() {
        return "nsuk".equals(waiterType) || "and".equals(waiterType);
    }

    public boolean isAndWaiter() {
        return "and".equals(waiterType);
    }

    public boolean canCook(String itemId) {
        return itemId != null && cook.contains(itemId);
    }

    public double cookPrice(String itemId) {
        return cookPrices.getOrDefault(itemId, 0.0);
    }

    public String randomCookItem(net.minecraft.util.RandomSource random) {
        if (cook.isEmpty()) return "";
        return cook.get(random.nextInt(cook.size()));
    }

    public RecipeDefinition recipeById(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) return null;
        for (RecipeDefinition r : recipes) {
            if (r.id().equals(recipeId)) return r;
        }
        return null;
    }

    public List<BlockPos> allSeatPositions() {
        return seats.stream().flatMap(s -> s.positions().stream()).toList();
    }

    public String defaultRecipeId() {
        return recipes.isEmpty() ? "" : recipes.getFirst().id();
    }

    public String randomRecipeId(net.minecraft.util.RandomSource random) {
        if (recipes.isEmpty()) return "";
        return recipes.get(random.nextInt(recipes.size())).id();
    }

    public record JobDefinition(String id, String name, String heldItem) {
        public JobDefinition {
            id = id != null && !id.isBlank() ? id.trim() : "chef";
            name = name != null && !name.isBlank() ? name.trim() : id;
            heldItem = heldItem != null ? heldItem.trim() : "";
        }
    }

    public record PointDefinition(String id, String type, List<BlockPos> positions) {
        public PointDefinition {
            id = id != null ? id.trim() : "";
            type = type != null ? type.trim() : "structure_pos";
            positions = positions != null ? List.copyOf(positions) : List.of();
        }
    }

    public record ContainerDefinition(String id, String type, List<BlockPos> positions) {
        public ContainerDefinition {
            id = id != null ? id.trim() : "container";
            type = type != null ? type.trim() : "structure_pos";
            positions = positions != null
                    ? positions.stream().filter(p -> p != null).map(BlockPos::immutable).distinct().toList()
                    : List.of();
        }
    }

    public record SeatDefinition(List<BlockPos> positions) {
        public SeatDefinition {
            positions = positions != null
                    ? positions.stream().filter(p -> p != null).map(BlockPos::immutable).distinct().toList()
                    : List.of();
        }
    }

    public record RecipeDefinition(String id, String name, String heldItem,
                                   List<ItemRequirement> inputItems,
                                   List<ItemRequirement> resultItems,
                                   int cookingTicks,
                                   List<CookingStep> cookingSteps) {
        public RecipeDefinition {
            id = id != null && !id.isBlank() ? id.trim() : "recipe";
            name = name != null && !name.isBlank() ? name.trim() : id;
            heldItem = heldItem != null ? heldItem.trim() : "";
            inputItems = inputItems != null ? List.copyOf(inputItems) : List.of();
            resultItems = resultItems != null ? List.copyOf(resultItems) : List.of();
            cookingTicks = Math.max(1, cookingTicks);
            cookingSteps = cookingSteps != null ? List.copyOf(cookingSteps) : List.of();
        }

        public String effectiveHeldItem(String fallback) {
            return heldItem.isBlank() ? fallback : heldItem;
        }
    }

    public record ItemRequirement(String itemId, int count) {
        public ItemRequirement {
            itemId = itemId != null ? itemId.trim() : "";
            count = Math.max(1, count);
        }
    }

    public record CookingStep(String action, String item, int count, int waitTicks) {
        public CookingStep {
            action = action != null ? action.trim() : "";
            item = item != null ? item.trim() : "";
            count = Math.max(1, count);
            waitTicks = Math.max(0, waitTicks);
        }
    }
}
