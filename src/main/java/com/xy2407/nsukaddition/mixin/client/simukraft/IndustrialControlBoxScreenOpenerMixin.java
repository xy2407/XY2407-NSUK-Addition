package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.industrial.IndustrialControlBoxScreenOpener;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 替换配方列表，添加搜索过滤和滚动状态保持功能。 */
@Mixin(IndustrialControlBoxScreenOpener.class)
public abstract class IndustrialControlBoxScreenOpenerMixin {
    private static final Class<?> LAYOUT_METRICS_CLASS;
    private static final Method RECIPE_ROW_METHOD;
    private static final Method LABEL_METHOD;
    private static final Method RECIPE_AREA_HEIGHT_METHOD;
    private static final Method RECIPE_ROW_HEIGHT_METHOD;

    private static String persistedSearchText = "";
    private static float persistedScrollValue = 0.0F;
    private static String persistedSelectedRecipeId = "";

    static {
        try {
            LAYOUT_METRICS_CLASS = Class.forName(
                    "client.cn.kafei.simukraft.client.industrial.IndustrialControlBoxScreenOpener$LayoutMetrics");
            RECIPE_ROW_METHOD = IndustrialControlBoxScreenOpener.class.getDeclaredMethod(
                    "recipeRow",
                    IndustrialControlBoxOpenResponsePacket.class,
                    IndustrialControlBoxOpenResponsePacket.RecipeEntry.class,
                    LAYOUT_METRICS_CLASS);
            RECIPE_ROW_METHOD.setAccessible(true);
            LABEL_METHOD = IndustrialControlBoxScreenOpener.class.getDeclaredMethod(
                    "label",
                    Component.class, Horizontal.class, int.class, int.class, TextWrap.class);
            LABEL_METHOD.setAccessible(true);
            RECIPE_AREA_HEIGHT_METHOD = LAYOUT_METRICS_CLASS.getDeclaredMethod("recipeAreaHeight");
            RECIPE_AREA_HEIGHT_METHOD.setAccessible(true);
            RECIPE_ROW_HEIGHT_METHOD = LAYOUT_METRICS_CLASS.getDeclaredMethod("recipeRowHeight");
            RECIPE_ROW_HEIGHT_METHOD.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize industrial control box GUI reflection", e);
        }
    }

    private static UIElement invokeRecipeRow(IndustrialControlBoxOpenResponsePacket packet,
                                             IndustrialControlBoxOpenResponsePacket.RecipeEntry recipe,
                                             Object metrics) {
        try {
            return (UIElement) RECIPE_ROW_METHOD.invoke(null, packet, recipe, metrics);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke recipeRow", e);
        }
    }

    private static Label invokeLabel(Component text, Horizontal horizontal, int color, int height, TextWrap wrap) {
        try {
            return (Label) LABEL_METHOD.invoke(null, text, horizontal, color, height, wrap);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke label", e);
        }
    }

    private static int recipeAreaHeight(Object metrics) {
        try {
            return (int) RECIPE_AREA_HEIGHT_METHOD.invoke(metrics);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to get recipeAreaHeight", e);
        }
    }

    private static int recipeRowHeight(Object metrics) {
        try {
            return (int) RECIPE_ROW_HEIGHT_METHOD.invoke(metrics);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to get recipeRowHeight", e);
        }
    }

    @Inject(method = "recipeList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$enhancedRecipeList(IndustrialControlBoxOpenResponsePacket packet,
                                                @Coerce Object metrics,
                                                CallbackInfoReturnable<UIElement> cir) {
        persistedSelectedRecipeId = packet.selectedRecipeId() == null ? "" : packet.selectedRecipeId();

        UIElement container = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(recipeAreaHeight(metrics));
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.marginTop(4);
        });
        container.setOverflowVisible(false);

        TextField searchField = new TextField();
        searchField.layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
        });
        searchField.textFieldStyle(style -> style
                .placeholder(Component.translatable("gui.xy2407_nsuk_addition.industrial.search_recipe"))
                .textColor(0xFF222222));
        searchField.setAnyString();
        searchField.setText(persistedSearchText, true);

        ScrollerView scrollerView = new ScrollerView();
        scrollerView.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        scrollerView.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO));
        scrollerView.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scrollerView.viewPort.layout(layout -> layout.paddingAll(0));
        scrollerView.verticalScroller.setOnValueChanged(value -> persistedScrollValue = value);

        var recipes = packet.recipes();
        if (recipes.isEmpty()) {
            scrollerView.addScrollViewChild(
                    invokeLabel(Component.translatable("gui.simukraft.industrial.no_recipes"),
                            Horizontal.CENTER, 0xFFFF7070, recipeRowHeight(metrics), TextWrap.HIDE));
        } else {
            List<RecipeRowEntry> recipeRows = new ArrayList<>(recipes.size());
            for (var recipe : recipes) {
                UIElement row = invokeRecipeRow(packet, recipe, metrics);
                recipeRows.add(new RecipeRowEntry(recipe.id(), row, buildSearchText(recipe)));
                scrollerView.addScrollViewChild(row);
            }

            Label noMatchLabel = invokeLabel(
                    Component.translatable("gui.xy2407_nsuk_addition.industrial.no_match"),
                    Horizontal.CENTER, 0xFFFF7070, recipeRowHeight(metrics), TextWrap.HIDE);
            noMatchLabel.setDisplay(false);
            scrollerView.addScrollViewChild(noMatchLabel);

            Runnable applyFilter = () -> {
                String query = searchField.getValue();
                persistedSearchText = query == null ? "" : query;
                String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
                boolean anyVisible = false;
                boolean hasSelectedVisible = false;
                for (RecipeRowEntry entry : recipeRows) {
                    boolean matches = normalizedQuery.isEmpty() || entry.searchIndex().contains(normalizedQuery);
                    entry.row().setDisplay(matches);
                    if (matches) {
                        anyVisible = true;
                        if (entry.recipeId().equals(persistedSelectedRecipeId)) {
                            hasSelectedVisible = true;
                        }
                    }
                }
                noMatchLabel.setDisplay(!anyVisible && !normalizedQuery.isEmpty());
                restoreScrollState(scrollerView, anyVisible, hasSelectedVisible);
            };

            searchField.setTextResponder(text -> applyFilter.run());
            applyFilter.run();
        }

        container.addChildren(searchField, scrollerView);
        cir.setReturnValue(container);
    }

    private static String buildSearchText(IndustrialControlBoxOpenResponsePacket.RecipeEntry recipe) {
        StringBuilder builder = new StringBuilder();
        appendToken(builder, recipe.name());
        appendToken(builder, recipe.id());
        recipe.inputs().forEach(item -> appendToken(builder, item.itemId()));
        recipe.outputs().forEach(item -> appendToken(builder, item.itemId()));
        return builder.toString();
    }

    private static void appendToken(StringBuilder builder, String token) {
        if (token == null || token.isBlank()) return;
        if (!builder.isEmpty()) builder.append(' ');
        builder.append(token.toLowerCase(Locale.ROOT));
    }

    private static void restoreScrollState(ScrollerView scrollerView, boolean anyVisible, boolean hasSelectedVisible) {
        if (!anyVisible) {
            persistedScrollValue = 0.0F;
            scrollerView.verticalScroller.setNormalizedValue(0.0F, true);
            return;
        }
        if (!hasSelectedVisible && !persistedSelectedRecipeId.isBlank()) {
            persistedScrollValue = 0.0F;
        }
        scrollerView.verticalScroller.setNormalizedValue(clamp01(persistedScrollValue), true);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        return Math.min(value, 1.0F);
    }

    private record RecipeRowEntry(String recipeId, UIElement row, String searchIndex) {}
}
