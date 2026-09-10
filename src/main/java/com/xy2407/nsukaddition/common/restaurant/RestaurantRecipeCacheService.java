package com.xy2407.nsukaddition.common.restaurant;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 餐厅菜品材料缓存：整道菜递归展开为基础料并全局缓存，容量70道，超出淘汰最早加入的菜品。 */
public final class RestaurantRecipeCacheService {

    private RestaurantRecipeCacheService() {
    }

    /** 缓存上限：最多缓存 70 个菜品的材料展开结果。 */
    private static final int MAX_DISHES = 70;

    // 插入序 LinkedHashMap，天然 FIFO：超限时淘汰最早加入的菜品
    private static final Map<String, List<Ingredient>> CACHE = new LinkedHashMap<>();

    /** 获取某菜品的全部基础料(含子配方递归展开)；命中缓存直接返回，未命中时解析并写入。空表示无此菜品或无可补材料。 */
    public static List<Ingredient> dishMaterials(ServerLevel level, String recipeId) {
        if (level == null || recipeId == null || recipeId.isBlank()) {
            return List.of();
        }
        synchronized (CACHE) {
            List<Ingredient> cached = CACHE.get(recipeId);
            if (cached != null) {
                return cached;
            }
        }
        List<Ingredient> resolved = resolve(level, recipeId);
        synchronized (CACHE) {
            if (!resolved.isEmpty()) {
                CACHE.put(recipeId, resolved);
                if (CACHE.size() > MAX_DISHES) {
                    Iterator<String> it = CACHE.keySet().iterator();
                    it.next();
                    it.remove();
                }
            }
        }
        return resolved;
    }

    /** 解析整道菜的配方直接料，并对每个配料递归展开为最基础原料（容器/桶/碗等被过滤）。 */
    private static List<Ingredient> resolve(ServerLevel level, String recipeId) {
        CookingWorkService.ResolvedRecipe recipe = CookingWorkService.findRecipe(level, recipeId);
        if (recipe == null) {
            return List.of();
        }
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient ing : recipe.ingredients()) {
            if (ing == null || ing.isEmpty()) {
                continue;
            }
            for (Ingredient leaf : RecipeDecompositionService.decompose(level, ing)) {
                if (leaf == null || leaf.isEmpty()) {
                    continue;
                }
                out.add(leaf);
            }
        }
        return List.copyOf(out);
    }
}