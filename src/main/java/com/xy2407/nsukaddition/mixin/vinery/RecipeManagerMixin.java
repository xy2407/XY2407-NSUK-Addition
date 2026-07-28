package com.xy2407.nsukaddition.mixin.vinery;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 屏蔽 Vinery 原酿造配方（wine_fermentation + apple_fermenting），统一改用 Kaleidoscope 酿造桶系统。 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    private static boolean shouldFilter(RecipeType<?> type) {
        String name = type.toString();
        return "vinery:wine_fermentation".equals(name) || "vinery:apple_fermenting".equals(name);
    }

    @Inject(method = "getAllRecipesFor", at = @At("HEAD"), cancellable = true)
    private <T extends Recipe<?>> void nsuk$filterAllRecipes(RecipeType<T> type, CallbackInfoReturnable<Collection<RecipeHolder<T>>> cir) {
        if (shouldFilter(type)) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void nsuk$filterGenericRecipe(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        if (shouldFilter(type)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
