package com.xy2407.nsukaddition.client.breeding;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.building.BuildingIntegrityUi;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenResponsePacket;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 养殖控制箱的客户端界面构建与交互，包含配方列表搜索、操作按钮和建筑边界渲染。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BreedingControlBoxScreenOpener {
    private static final int MAX_PANEL_WIDTH = 480;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MIN_PANEL_HEIGHT = 190;
    private static final int ROW_BUTTON_BASE = 0xFFE0E0E0;
    private static final int ROW_BUTTON_HOVER = 0xFFF0F0F0;
    private static final int ROW_BUTTON_SELECTED = 0xFFD8EAD8;
    private static final int ROW_BUTTON_BORDER = 0xFF1E1E1E;
    private static final float TEXT_ROLL_SPEED = 0.25F;
    private static BlockPos openedBoxPos;
    private static String persistedSearchText = "";
    private static float persistedScrollValue = 0.0F;
    private static String persistedSelectedRecipeId = "";

    private BreedingControlBoxScreenOpener() {
    }

    public static void open(BreedingControlBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        openedBoxPos = packet.boxPos().immutable();
        syncDisplayedBounds(packet);
        mc.execute(() -> mc.setScreen(new BreedingControlBoxScreen(createUi(packet), Component.empty())));
    }

    public static void refreshIfOpen(BreedingControlBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || openedBoxPos == null || !openedBoxPos.equals(packet.boxPos())) return;
        syncDisplayedBounds(packet);
        mc.execute(() -> {
            if (openedBoxPos != null && openedBoxPos.equals(packet.boxPos()) && mc.screen instanceof BreedingControlBoxScreen) {
                mc.setScreen(new BreedingControlBoxScreen(createUi(packet), Component.empty()));
            }
        });
    }

    private static ModularUI createUi(BreedingControlBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        LayoutMetrics metrics = layoutMetrics(screenWidth, screenHeight);
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(metrics.rootPadding());
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(metrics.panelWidth());
            layout.height(metrics.panelHeight());
            layout.paddingAll(metrics.panelPadding());
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
        }).addClass("simukraft_panel");

        panel.addChild(titleBar(metrics));
        panel.addChild(header(packet, metrics));
        panel.addChild(recipeList(packet, metrics));
        panel.addChild(actionRow(packet, metrics));

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(LayoutMetrics metrics) {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.titleBarHeight());
        });
        bar.addChild(label(Component.translatable("gui.xy2407_nsuk_addition.breeding.title"), Horizontal.CENTER, 0xFFFFFFFF, metrics.titleBarHeight(), TextWrap.HIDE));
        bar.addChild(panelTopButton("gui.button.done", metrics.doneButtonWidth(), metrics.doneButtonHeight(), BreedingControlBoxScreenOpener::close));
        return bar;
    }

    private static UIElement header(BreedingControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.headerHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
        });
        UIElement info = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.innerGap());
            layout.flexShrink(1);
        });
        info.setOverflowVisible(false);
        info.addChild(label(buildingLine(packet), Horizontal.LEFT, 0xFFF5F5A0, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(label(definitionLine(packet), Horizontal.LEFT, packet.definitionValid() ? 0xFFF5F5A0 : 0xFFFF7070, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(label(workerLine(packet), Horizontal.LEFT, 0xFFF5F5A0, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(label(statusLine(packet), Horizontal.LEFT, 0xFFE0E0FF, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(BuildingIntegrityUi.progressBar(packet.integrityAvailable(), packet.integrityPercent(), metrics.integrityBarHeight()));
        row.addChild(info);

        UIElement tools = new UIElement().layout(layout -> {
            layout.width(metrics.toolWidth());
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.innerGap());
            layout.flexShrink(0);
        });
        tools.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.breeding.demolish"), () -> demolish(packet), packet.hasBuilding(), metrics.toolWidth(), metrics.toolButtonHeight()));
        tools.addChild(flatButton(boundsText(packet), () -> toggleBounds(packet), packet.hasBuildingBounds(), metrics.toolWidth(), metrics.toolButtonHeight()));
        tools.addChild(flatButton(BuildingIntegrityUi.repairText(packet.integrityRepairCost()), () -> action(packet, BreedingControlBoxActionPacket.Action.REPAIR_BUILDING, ""), packet.integrityAvailable() && (packet.integrityRepairableBlocks() > 0 || packet.integrityManualRepairBlocks() > 0), metrics.toolWidth(), metrics.toolButtonHeight()));
        row.addChild(tools);
        return row;
    }

    private static UIElement recipeList(BreedingControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        persistedSelectedRecipeId = packet.selectedRecipeId() == null ? "" : packet.selectedRecipeId();

        UIElement container = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.recipeAreaHeight());
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.marginTop(metrics.innerGap());
        });
        container.setOverflowVisible(false);

        TextField searchField = new TextField();
        searchField.layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
        });
        searchField.textFieldStyle(style -> style
                .placeholder(Component.translatable("gui.xy2407_nsuk_addition.breeding.search_recipe"))
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

        List<BreedingControlBoxOpenResponsePacket.RecipeEntry> recipes = packet.recipes();
        if (recipes.isEmpty()) {
            scrollerView.addScrollViewChild(
                    label(Component.translatable("gui.xy2407_nsuk_addition.breeding.no_recipes"),
                            Horizontal.CENTER, 0xFFFF7070, metrics.recipeRowHeight(), TextWrap.HIDE));
        } else {
            List<RecipeRowEntry> recipeRows = new ArrayList<>(recipes.size());
            for (var recipe : recipes) {
                UIElement row = recipeRow(packet, recipe, metrics);
                recipeRows.add(new RecipeRowEntry(
                        recipe.id(),
                        row,
                        buildSearchText(recipe)
                ));
                scrollerView.addScrollViewChild(row);
            }

            Label noMatchLabel = label(
                    Component.translatable("gui.xy2407_nsuk_addition.breeding.no_match"),
                    Horizontal.CENTER, 0xFFFF7070, metrics.recipeRowHeight(), TextWrap.HIDE);
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
        return container;
    }

    private static UIElement actionRow(BreedingControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.actionHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.gap());
        });
        row.addChild(flatButton(toggleText(packet), () -> action(packet, BreedingControlBoxActionPacket.Action.TOGGLE_RUN, ""), packet.definitionValid() && packet.hasWorker(), metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.breeding.hire"), () -> hire(packet), !packet.hasWorker(), metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(flatButton(Component.translatable("gui.xy2407_nsuk_addition.breeding.fire"), () -> action(packet, BreedingControlBoxActionPacket.Action.FIRE, ""), packet.hasWorker(), metrics.actionWidth(), metrics.actionHeight()));
        return row;
    }

    private static UIElement recipeRow(BreedingControlBoxOpenResponsePacket packet, BreedingControlBoxOpenResponsePacket.RecipeEntry recipe, LayoutMetrics metrics) {
        boolean selected = recipe.id().equals(packet.selectedRecipeId());
        Button button = new Button().noText();
        button.buttonStyle(style -> style
                .baseTexture(rowButtonTexture(selected, false))
                .hoverTexture(rowButtonTexture(selected, true))
                .pressedTexture(rowButtonTexture(true, true)));
        button.setOnClick(event -> action(packet, BreedingControlBoxActionPacket.Action.SELECT_RECIPE, recipe.id()));
        button.layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.recipeRowHeight());
            layout.paddingLeft(metrics.rowPadding());
            layout.paddingRight(metrics.rowPadding());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.innerGap());
        });
        button.addChild(label(Component.literal(selected ? ">" : ""), Horizontal.CENTER, 0xFF1E1E1E, metrics.recipeRowHeight(), TextWrap.HIDE, false).layout(layout -> {
            layout.width(metrics.selectorWidth());
            layout.flexShrink(0);
        }));
        button.addChild(label(Component.literal(recipe.name()), Horizontal.LEFT, 0xFF222222, metrics.recipeRowHeight(), TextWrap.HOVER_ROLL, false).layout(layout -> {
            layout.flex(1);
            layout.flexShrink(1);
        }));
        button.addChild(itemStrip(recipe.inputs(), metrics.inputStripWidth(), metrics.inputLimit(), metrics));
        button.addChild(label(Component.literal("->"), Horizontal.CENTER, 0xFFFFFFFF, metrics.recipeRowHeight(), TextWrap.HIDE).layout(layout -> {
            layout.width(metrics.arrowWidth());
            layout.flexShrink(0);
        }));
        button.addChild(itemStrip(recipe.outputs(), metrics.outputStripWidth(), metrics.outputLimit(), metrics));
        return button;
    }

    private static UIElement itemStrip(List<BreedingControlBoxOpenResponsePacket.ItemEntry> items, int width, int limit, LayoutMetrics metrics) {
        UIElement strip = new UIElement().layout(layout -> {
            layout.width(width);
            layout.height(metrics.iconSize());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.iconGap());
            layout.flexShrink(0);
        });
        strip.setOverflowVisible(false);
        items.stream().limit(limit).forEach(item -> {
            if (!item.connector().isBlank()) {
                strip.addChild(connectorLabel(item.connector(), metrics));
            }
            strip.addChild(icon(item, metrics.iconSize()));
        });
        return strip;
    }

    private static UIElement connectorLabel(String connector, LayoutMetrics metrics) {
        return label(Component.literal(connector), Horizontal.CENTER, 0xFF222222, metrics.iconSize(), TextWrap.HIDE, false).layout(layout -> {
            layout.width(connectorWidth(metrics));
            layout.height(metrics.iconSize());
            layout.flexShrink(0);
        });
    }

    private static UIElement icon(BreedingControlBoxOpenResponsePacket.ItemEntry item, int size) {
        return new UIElement().layout(layout -> {
            layout.width(size);
            layout.height(size);
            layout.flexShrink(0);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(stack(item))));
    }

    private static Button panelTopButton(String key, int width, int height, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(width);
            layout.height(height);
        });
        return button;
    }

    private static Button flatButton(Component text, Runnable action, boolean active, int width, int height) {
        Button button = new Button();
        button.setText(text);
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.setActive(active);
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        return button;
    }

    private static IGuiTexture rowButtonTexture(boolean selected, boolean hover) {
        int color = selected ? ROW_BUTTON_SELECTED : hover ? ROW_BUTTON_HOVER : ROW_BUTTON_BASE;
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(-1, ROW_BUTTON_BORDER));
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap) {
        return label(text, horizontal, color, height, wrap, true);
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap, boolean shadow) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color).textShadow(shadow)
                .textWrap(wrap)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component buildingLine(BreedingControlBoxOpenResponsePacket packet) {
        Component value = packet.hasBuilding() ? Component.literal(packet.buildingName()) : Component.translatable("gui.xy2407_nsuk_addition.breeding.none");
        return Component.translatable("gui.xy2407_nsuk_addition.breeding.building_line", value);
    }

    private static Component definitionLine(BreedingControlBoxOpenResponsePacket packet) {
        Component value = packet.definitionValid() ? Component.literal(packet.definitionName()) : Component.translatable("gui.xy2407_nsuk_addition.breeding.definition_missing");
        return Component.translatable("gui.xy2407_nsuk_addition.breeding.definition_line", value);
    }

    private static Component workerLine(BreedingControlBoxOpenResponsePacket packet) {
        Component value = packet.hasWorker() ? Component.literal(packet.workerName()) : Component.translatable("gui.xy2407_nsuk_addition.breeding.none");
        return Component.translatable("gui.xy2407_nsuk_addition.breeding.worker_line", value);
    }

    private static Component statusLine(BreedingControlBoxOpenResponsePacket packet) {
        Component status = Component.translatable(packet.statusKey());
        if (!packet.statusText().isBlank()) {
            status = status.copy().append(Component.literal(" " + packet.statusText()));
        }
        return Component.translatable("gui.xy2407_nsuk_addition.breeding.status_line", status);
    }

    private static Component toggleText(BreedingControlBoxOpenResponsePacket packet) {
        return Component.translatable(packet.running() ? "gui.xy2407_nsuk_addition.breeding.stop" : "gui.xy2407_nsuk_addition.breeding.start");
    }

    private static Component boundsText(BreedingControlBoxOpenResponsePacket packet) {
        return Component.translatable("gui.xy2407_nsuk_addition.breeding.show_building_bounds", onOffText(BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos())));
    }

    private static Component onOffText(boolean enabled) {
        return Component.translatable(enabled ? "gui.switch.on" : "gui.switch.off");
    }

    private static void toggleBounds(BreedingControlBoxOpenResponsePacket packet) {
        boolean next = !BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos());
        if (next) {
            showBounds(packet);
        } else {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new BreedingControlBoxScreen(createUi(packet), Component.empty()));
    }

    private static void syncDisplayedBounds(BreedingControlBoxOpenResponsePacket packet) {
        if (!BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos())) return;
        if (packet.hasBuildingBounds()) {
            showBounds(packet);
        } else {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        }
    }

    private static void showBounds(BreedingControlBoxOpenResponsePacket packet) {
        if (!packet.hasBuildingBounds()) return;
        AABB bounds = new AABB(
                packet.boundsMin().getX(), packet.boundsMin().getY(), packet.boundsMin().getZ(),
                packet.boundsMax().getX() + 1, packet.boundsMax().getY() + 1, packet.boundsMax().getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisibleWithMarkers(packet.boxPos(), bounds,
                packet.pointMarkers().stream()
                        .map(m -> new BuildingBoundsRenderer.DisplayMarker(m.pos(), m.color()))
                        .toList(),
                true);
    }

    private static void action(BreedingControlBoxOpenResponsePacket packet, BreedingControlBoxActionPacket.Action action, String recipeId) {
        PacketDistributor.sendToServer(new BreedingControlBoxActionPacket(packet.boxPos(), action, recipeId));
    }

    private static void hire(BreedingControlBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), BreedingConstants.HIRE_SOURCE_TYPE, BreedingConstants.HIRE_ROLE);
    }

    private static void demolish(BreedingControlBoxOpenResponsePacket packet) {
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        openedBoxPos = null;
        persistedSearchText = "";
        persistedScrollValue = 0.0F;
        persistedSelectedRecipeId = "";
        Minecraft.getInstance().setScreen(null);
        PacketDistributor.sendToServer(new BreedingControlBoxDemolishPacket(packet.boxPos()));
    }

    private static ItemStack stack(BreedingControlBoxOpenResponsePacket.ItemEntry item) {
        if (item.itemId().isBlank()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(item.itemId());
        if (id == null) return ItemStack.EMPTY;
        var regItem = BuiltInRegistries.ITEM.getOptional(id);
        return regItem.filter(i -> i != net.minecraft.world.item.Items.AIR)
                .map(i -> new ItemStack(i, Math.max(1, item.count())))
                .orElse(ItemStack.EMPTY);
    }

    private static void close() {
        openedBoxPos = null;
        persistedSearchText = "";
        persistedScrollValue = 0.0F;
        persistedSelectedRecipeId = "";
        Minecraft.getInstance().setScreen(null);
    }

    private static String buildSearchText(BreedingControlBoxOpenResponsePacket.RecipeEntry recipe) {
        StringBuilder builder = new StringBuilder();
        appendToken(builder, recipe.name());
        appendToken(builder, recipe.id());
        recipe.inputs().forEach(item -> appendToken(builder, item.itemId()));
        recipe.outputs().forEach(item -> appendToken(builder, item.itemId()));
        return builder.toString();
    }

    private static void appendToken(StringBuilder builder, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
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
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(value, 1.0F);
    }

    private static LayoutMetrics layoutMetrics(int screenWidth, int screenHeight) {
        int rootPadding = clamp(Math.round(Math.min(screenWidth, screenHeight) * 0.018F), 4, 10);
        int availableWidth = Math.max(MIN_PANEL_WIDTH, screenWidth - rootPadding * 2);
        int availableHeight = Math.max(MIN_PANEL_HEIGHT, screenHeight - rootPadding * 2 - 24);
        int panelWidth = clamp(Math.min(MAX_PANEL_WIDTH, availableWidth), Math.min(MIN_PANEL_WIDTH, availableWidth), availableWidth);
        int panelHeight = clamp(Math.min(MAX_PANEL_HEIGHT, availableHeight), Math.min(MIN_PANEL_HEIGHT, availableHeight), availableHeight);
        int panelPadding = clamp(Math.round(panelWidth * 0.024F), 6, 12);
        int gap = clamp(Math.round(panelHeight * 0.018F), 3, 6);
        int innerGap = clamp(gap - 1, 2, 5);
        int titleHeight = clamp(Math.round(panelHeight * 0.080F), 14, 20);
        int doneButtonHeight = clamp(Math.round(panelHeight * 0.078F), 18, 24);
        int titleBarHeight = Math.max(titleHeight, doneButtonHeight);
        int infoLineHeight = clamp(Math.round(panelHeight * 0.056F), 11, 16);
        int integrityBarHeight = clamp(Math.round(panelHeight * 0.060F), 12, 18);
        int toolButtonHeight = clamp(Math.round(panelHeight * 0.085F), 18, 24);
        int toolWidth = clamp(Math.round(panelWidth * 0.235F), 86, 112);
        int headerHeight = Math.max(infoLineHeight * 4 + integrityBarHeight + innerGap * 4, toolButtonHeight * 3 + innerGap * 2);
        int actionHeight = clamp(Math.round(panelHeight * 0.078F), 20, 24);
        int actionWidth = clamp((panelWidth - panelPadding * 2 - gap * 2) / 3, 84, 132);
        int recipeAreaHeight = Math.max(28, panelHeight - panelPadding * 2 - titleBarHeight - headerHeight - actionHeight - gap * 4 - innerGap);
        int recipeRowHeight = clamp(Math.round(panelHeight * 0.098F), 24, 32);
        int iconSize = clamp(recipeRowHeight - 10, 14, 20);
        int iconGap = clamp(iconSize / 5, 2, 4);
        int selectorWidth = clamp(Math.round(panelWidth * 0.035F), 12, 18);
        int arrowWidth = clamp(Math.round(panelWidth * 0.050F), 18, 26);
        int inputStripWidth = clamp(Math.round(panelWidth * 0.280F), 86, 138);
        int outputStripWidth = clamp(Math.round(panelWidth * 0.180F), 56, 88);
        int inputLimit = Math.max(1, inputStripWidth / Math.max(1, iconSize + iconGap));
        int outputLimit = Math.max(1, outputStripWidth / Math.max(1, iconSize + iconGap));
        int rowPadding = clamp(Math.round(panelWidth * 0.014F), 4, 8);
        int doneButtonWidth = clamp(Math.round(panelWidth * 0.16F), 50, 76);
        return new LayoutMetrics(rootPadding, panelWidth, panelHeight, panelPadding, gap, innerGap, titleBarHeight, doneButtonHeight, infoLineHeight, integrityBarHeight, headerHeight, toolWidth, toolButtonHeight, recipeAreaHeight, recipeRowHeight, iconSize, iconGap, selectorWidth, arrowWidth, inputStripWidth, outputStripWidth, inputLimit, outputLimit, rowPadding, actionWidth, actionHeight, doneButtonWidth);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int connectorWidth(LayoutMetrics metrics) {
        return clamp(metrics.iconSize() / 2, 6, 10);
    }

    private record RecipeRowEntry(String recipeId, UIElement row, String searchIndex) {
    }

    private record LayoutMetrics(int rootPadding,
                                 int panelWidth,
                                 int panelHeight,
                                 int panelPadding,
                                 int gap,
                                 int innerGap,
                                 int titleBarHeight,
                                 int doneButtonHeight,
                                 int infoLineHeight,
                                 int integrityBarHeight,
                                 int headerHeight,
                                 int toolWidth,
                                 int toolButtonHeight,
                                 int recipeAreaHeight,
                                 int recipeRowHeight,
                                 int iconSize,
                                 int iconGap,
                                 int selectorWidth,
                                 int arrowWidth,
                                 int inputStripWidth,
                                 int outputStripWidth,
                                 int inputLimit,
                                 int outputLimit,
                                 int rowPadding,
                                 int actionWidth,
                                 int actionHeight,
                                 int doneButtonWidth) {
    }

    private static class BreedingControlBoxScreen extends ModularUIScreen {
        protected BreedingControlBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }
    }
}
