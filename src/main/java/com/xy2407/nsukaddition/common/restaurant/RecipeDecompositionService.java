package com.xy2407.nsukaddition.common.restaurant;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 配方分解服务：把"可制造的中间材料"递归展开为其最基础原料，
 * 用于餐厅补货(只补基础料、不要求仓库备中间物)与厨师途合中间物。
 * 覆盖工作台与磨石机等任意产出配方；原料标签(tag)作为叶子原样保留，保证按标签匹配也生效。
 */
public final class RecipeDecompositionService {

    private static final int MAX_DEPTH = 4;

    private RecipeDecompositionService() {
    }

    /** 把单个 material ingredient 展开为最基础原料列表（含不可再合成的 tag/单物品叶）。空材料返回空。 */
    public static List<Ingredient> decompose(ServerLevel level, Ingredient root) {
        List<Ingredient> out = new ArrayList<>();
        if (root == null || root.isEmpty()) {
            return out;
        }
        decompose(level, root, 0, new HashSet<>(), out);
        return out;
    }

    private static void decompose(ServerLevel level, Ingredient ing, int depth,
                                  Set<String> visited, List<Ingredient> out) {
        ItemStack rep = firstNonEmpty(ing.getItems());
        if (rep == null || rep.isEmpty()) {
            out.add(ing);
            return;
        }
        Item item = rep.getItem();
        // 容器/工具类材料(桶、水桶、木碗、瓶等)不作为基础料展开，避免补货去弄容器、烹饪因缺容器卡住。
        if (isToolOrContainerItem(item)) {
            return;
        }
        String key = BuiltInRegistries.ITEM.getKey(item).toString();
        // 已展开过或超过深度：把原 ingredient 作为产物叶保留（含 tag），避免死循环
        if (!visited.add(key) || depth >= MAX_DEPTH) {
            out.add(ing);
            return;
        }
        Recipe<?> recipe = recipeFor(level, item);
        if (recipe == null) {
            // 无任何可产出它的配方：不可再分解，原样保留（tag / 单物品）
            out.add(ing);
            return;
        }
        for (Ingredient raw : recipe.getIngredients()) {
            if (raw != null && !raw.isEmpty()) {
                decompose(level, raw, depth + 1, visited, out);
            }
        }
    }

    /** 配方中的工具/容器类材料（桶、水桶、木碗、玻璃瓶等）：仅作容器，不消耗、不补货、不卡做菜。 */
    public static boolean isToolOrContainerItem(net.minecraft.world.item.Item item) {
        if (item == null) return false;
        if (item instanceof net.minecraft.world.item.BucketItem) return true;
        if (item instanceof net.minecraft.world.item.MilkBucketItem) return true;
        if (item == net.minecraft.world.item.Items.BOWL) return true;
        if (item == net.minecraft.world.item.Items.GLASS_BOTTLE) return true;
        if (item == net.minecraft.world.item.Items.POTION) return true;
        if (item == net.minecraft.world.item.Items.BUCKET) return true;
        if (item == net.minecraft.world.item.Items.WATER_BUCKET) return true;
        if (item == net.minecraft.world.item.Items.LAVA_BUCKET) return true;
        if (item == net.minecraft.world.item.Items.MILK_BUCKET) return true;
        if (item == net.minecraft.world.item.Items.COD_BUCKET
                || item == net.minecraft.world.item.Items.SALMON_BUCKET
                || item == net.minecraft.world.item.Items.PUFFERFISH_BUCKET
                || item == net.minecraft.world.item.Items.TROPICAL_FISH_BUCKET
                || item == net.minecraft.world.item.Items.AXOLOTL_BUCKET
                || item == net.minecraft.world.item.Items.TADPOLE_BUCKET) return true;
        // 各模组的碗/杯/瓶/勺等容器类物品：通过物品名称粗判，避免逐一硬编码
        String id = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(java.util.Locale.ROOT);
        if (id.endsWith("_bowl") || id.equals("bowl")
                || id.endsWith("_cup") || id.equals("cup")
                || id.endsWith("_glass") || id.equals("glass")
                || id.endsWith("_bottle") || id.endsWith("_bucket")
                || id.contains("mug") || id.endsWith("_jug")
                || id.endsWith("_plate") || id.endsWith("_tray")
                || id.endsWith("_knife") || id.endsWith("_spoon") || id.endsWith("_fork")
                || id.endsWith("_shovel") || id.endsWith("_cleaver")
                || id.endsWith("_pot") || id.equals("pot")) {
            return true;
        }
        return false;
    }

    /** 判断给定物品是否为可制造的中间物（存在产出它的某类配方，含工作台/磨石机等）。 */
    public static boolean isCraftableIntermediate(ServerLevel level, Item item) {
        return item != null && recipeFor(level, item) != null;
    }

    /** 获取产出该物品的配方（不限类型，覆盖工作台与磨石机等；无则 null）。 */
    public static Recipe<?> recipeFor(ServerLevel level, Item item) {
        var regs = level.registryAccess();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(regs);
            if (!result.isEmpty() && result.getItem() == item) {
                return recipe;
            }
        }
        return null;
    }

    private static ItemStack firstNonEmpty(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }
}