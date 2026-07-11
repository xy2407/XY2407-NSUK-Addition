package com.xy2407.nsukaddition.client.colony;

import client.cn.kafei.simukraft.client.citizen.CitizenAvatarFactory;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SimuKraftWindowFrame;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.xy2407.nsukaddition.common.network.colony.ColonyCitizenRelocatePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCreatePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyDeletePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyRenamePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 附属地核心方块客户端界面，完全复刻城市核心方块的窗口框架和标签页布局。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ColonyCoreScreenOpener {

    private static final int BUTTON_WIDTH = 120;
    private static final int COLLAPSED_SIDEBAR_WIDTH = 18;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BACK_BUTTON_WIDTH = 52;
    private static final int BACK_BUTTON_HEIGHT = 20;
    private static volatile ColonyWindow activeWindow;

    private ColonyCoreScreenOpener() {}

    public static void open(ColonyCoreOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ColonyChunkClientCache.getInstance().updateFromPacket(
                packet.colonyId(), packet.name(), packet.parentCityName(), packet.colonyChunks());
        minecraft.execute(() -> minecraft.setScreen(
                new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(ColonyCoreOpenResponsePacket packet) {
        ColonyWindow window = new ColonyWindow(packet);
        UIElement root = createWindowRoot(window);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement createWindowRoot(ColonyWindow window) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        return SimuKraftWindowFrame.create(
                screenSize,
                Component.translatable("gui.xy2407_nsuk_addition.colony.title"),
                workspace(window),
                ColonyCoreScreenOpener::close);
    }

    private static UIElement workspace(ColonyWindow window) {
        UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.paddingAll(8);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(8);
            layout.alignItems(AlignItems.STRETCH);
        });
        body.addChild(window.sidebarContainer);
        body.addChild(window.rightTabs);
        return body;
    }

    private static ScrollerView scrollable(UIElement child) {
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        scroller.addScrollViewChild(child);
        return scroller;
    }

    // 左侧菜单列，未创建时显示创建按钮，已创建时显示完整菜单
    private static UIElement menuColumn(ColonyCoreOpenResponsePacket packet, ColonyWindow window) {
        UIElement menu = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(5);
            layout.alignItems(AlignItems.STRETCH);
        });
        if (!packet.hasColony()) {
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_create",
                    () -> window.openTab("create", "gui.xy2407_nsuk_addition.colony.menu_create", createPanel(packet))));
        } else if (packet.canManageColony()) {
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_info",
                    () -> window.openTab("info", "gui.xy2407_nsuk_addition.colony.menu_info", scrollable(contentPanel(packet)))));
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_map",
                    () -> window.openTab("map", "gui.xy2407_nsuk_addition.colony.menu_map", mapPanel(packet))));
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_edit",
                    () -> window.openTab("edit", "gui.xy2407_nsuk_addition.colony.menu_edit", scrollable(editPanel(packet)))));
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_citizens",
                    () -> window.openTab("citizens", "gui.xy2407_nsuk_addition.colony.menu_citizens", scrollable(citizensPanel(packet)))));
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_relocate",
                    () -> window.openTab("relocate", "gui.xy2407_nsuk_addition.colony.menu_relocate", scrollable(relocatePanel(packet)))));
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_move",
                    () -> window.openTab("move", "gui.xy2407_nsuk_addition.colony.menu_move", scrollable(movePanel(packet)))));
        } else {
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_info",
                    () -> window.openTab("info", "gui.xy2407_nsuk_addition.colony.menu_info", scrollable(contentPanel(packet)))));
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.colony.menu_map",
                    () -> window.openTab("map", "gui.xy2407_nsuk_addition.colony.menu_map", mapPanel(packet))));
        }
        menu.addChild(menuSpacer());
        menu.addChild(closeButton(ColonyCoreScreenOpener::close));
        return menu;
    }

    // ---- 创建面板 ----

    private static UIElement createPanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.create_desc")));
        panel.addChild(contentSpacer());
        TextField nameField = textField("", 200);
        nameField.getTextFieldStyle().placeholder(Component.translatable("gui.xy2407_nsuk_addition.colony.name_placeholder"));
        panel.addChild(nameField);
        if (packet.canCreateColony()) {
            panel.addChild(contentButton("gui.xy2407_nsuk_addition.colony.create",
                    () -> PacketDistributor.sendToServer(new ColonyCreatePacket(packet.corePos(), nameField.getValue()))));
        } else {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.cannot_create")));
        }
        return scrollable(panel);
    }

    // ---- 信息面板 ----

    private static UIElement contentPanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        if (!packet.hasColony()) {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.no_colony")));
        } else {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.info_name", packet.name())));
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.info_parent_city", packet.parentCityName())));
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.info_core_pos",
                    packet.colonyCorePos().getX(), packet.colonyCorePos().getY(), packet.colonyCorePos().getZ())));
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.info_chunks", packet.usedChunks())));
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.info_chunk_pool",
                    packet.usedPoolChunks(), packet.totalChunkPool())));
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.info_citizens", packet.citizenCount())));
        }
        return panel;
    }

    // ---- 地图面板 ----

    private static UIElement mapPanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        panel.addChild(new ColonyChunkMapElement(packet));
        return panel;
    }

    // ---- 编辑面板 ----

    private static UIElement editPanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.edit_rename_title")));
        TextField renameField = textField(packet.name(), 200);
        panel.addChild(renameField);
        panel.addChild(contentButton("gui.xy2407_nsuk_addition.colony.edit_rename",
                () -> PacketDistributor.sendToServer(new ColonyRenamePacket(packet.colonyId(), renameField.getValue()))));
        panel.addChild(contentSpacer());
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.edit_delete_title")));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.edit_delete_tip", packet.name())));
        TextField deleteField = textField("", 200);
        deleteField.getTextFieldStyle().placeholder(Component.translatable("gui.xy2407_nsuk_addition.colony.edit_delete_placeholder"));
        panel.addChild(deleteField);
        panel.addChild(contentButton("gui.xy2407_nsuk_addition.colony.edit_delete",
                () -> PacketDistributor.sendToServer(new ColonyDeletePacket(packet.colonyId(), deleteField.getValue()))));
        return panel;
    }

    // ---- 市民管理面板 ----

    private static UIElement citizensPanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.citizens_count", packet.citizenCount())));
        panel.addChild(contentSpacer());
        if (packet.localCitizens() != null && !packet.localCitizens().isEmpty()) {
            for (var c : packet.localCitizens()) {
                panel.addChild(citizenRow(c));
            }
        } else {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.citizens_empty")));
        }
        return panel;
    }

    // 参照 CityCoreScreenOpener.citizenRow 渲染NPC头像+职位+年龄+性别+住宅信息
    private static UIElement citizenRow(ColonyCoreOpenResponsePacket.LocalCitizen citizen) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(44);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.CENTER);
        });
        // 头像
        UIElement avatar = CitizenAvatarFactory.createHead(citizen.skinPath(), 0xFFFFFFFF);
        avatar.layout(layout -> {
            layout.width(32);
            layout.height(32);
            layout.flexShrink(0);
        });
        row.addChild(avatar);
        // 左侧信息：职位+年龄+性别
        UIElement info = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
        });
        info.addChild(line(Component.translatable("screen.simukraft.city_core.citizen_manage.row_info",
                Component.translatable(citizen.jobKey()),
                Component.translatable(citizen.workStatusKey()),
                String.valueOf(citizen.age()),
                Component.translatable("screen.simukraft.city_core.citizen_manage.gender_" + citizen.gender()))));
        info.addChild(line(Component.literal(citizen.citizenName() == null || citizen.citizenName().isBlank() ? "-" : citizen.citizenName())));
        row.addChild(info);
        // 右侧：住宅信息
        if (citizen.hasHome() && citizen.homeName() != null && !citizen.homeName().isBlank()) {
            row.addChild(line(Component.literal(citizen.homeName()).withStyle(s -> s.withColor(0xFF66FF66))));
        } else {
            row.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.no_home").withStyle(s -> s.withColor(0xFFFF6666))));
        }
        return row;
    }

    // ---- 人口迁移面板 ----

    private static UIElement relocatePanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.relocate_desc")));
        panel.addChild(contentSpacer());
        var others = packet.otherCitizens();
        if (others == null || others.isEmpty()) {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.relocate_empty")));
        } else {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.relocate_available", others.size())));
            for (var citizen : others) {
                UIElement row = new UIElement().layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(24);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(4);
                    layout.alignItems(AlignItems.CENTER);
                });
                Label nameLabel = line(Component.literal(citizen.citizenName()));
                nameLabel.layout(layout -> { layout.flex(1); layout.height(13); });
                row.addChild(nameLabel);
                Label sourceLabel = line(Component.literal(citizen.sourceTerritory()));
                sourceLabel.layout(layout -> { layout.width(60); layout.height(13); });
                row.addChild(sourceLabel);
                Button relocateBtn = contentButton("gui.xy2407_nsuk_addition.colony.relocate_button", () -> {
                    PacketDistributor.sendToServer(new ColonyCitizenRelocatePacket(citizen.citizenId(), packet.colonyId()));
                    close();
                });
                relocateBtn.layout(layout -> { layout.width(48); layout.height(18); layout.flexShrink(0); });
                row.addChild(relocateBtn);
                panel.addChild(row);
            }
        }
        return panel;
    }

    // ---- 核心迁移面板 ----

    private static UIElement movePanel(ColonyCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.move_desc")));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.colony.move_restriction")));
        panel.addChild(contentSpacer());
        panel.addChild(contentButton("gui.xy2407_nsuk_addition.colony.move_button", () -> {
            close();
            ColonyCoreMovePreview.enter(packet.corePos(), packet.colonyId());
        }));
        return panel;
    }

    // ---- UI 辅助方法 ----

    private static UIElement basePanel() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(10);
            layout.gapAll(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
    }

    private static UIElement menuSpacer() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
    }

    private static UIElement contentSpacer() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
    }

    private static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
    }

    private static Label line(Component component) {
        Label label = new Label();
        label.setText(component);
        label.layout(layout -> {
            layout.height(13);
            layout.widthPercent(100);
        });
        return label;
    }

    private static UIElement sidebarHeader(boolean collapsed, Runnable action) {
        UIElement header = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_START);
            layout.paddingLeft(2);
        });
        header.addChild(sidebarToggleButton(collapsed, action));
        return header;
    }

    private static UIElement sidebarToggleButton(boolean collapsed, Runnable action) {
        Button button = new Button().noText();
        button.addChild(new UIElement()
                .layout(layout -> layout.width(10).height(10))
                .style(style -> style.backgroundTexture(collapsed ? Icons.EXPAND_HORIZONTAL : Icons.COLLAPSE_HORIZONTAL)));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.width(14);
            layout.height(14);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button menuButton(String key, Runnable action) {
        Button button = baseButton(key, action);
        button.layout(layout -> {
            layout.height(BUTTON_HEIGHT);
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button closeButton(Runnable action) {
        Button button = baseButton("gui.xy2407_nsuk_addition.colony.close", action);
        button.layout(layout -> {
            layout.width(BACK_BUTTON_WIDTH);
            layout.height(BACK_BUTTON_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static Button contentButton(String key, Runnable action) {
        Button button = baseButton(key, action);
        button.layout(layout -> {
            layout.width(100);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    private static TextField textField(String value, int width) {
        TextField field = new TextField();
        field.setText(value == null ? "" : value);
        field.layout(layout -> {
            layout.width(width);
            layout.height(20);
        });
        return field;
    }

    private static Button baseButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        return button;
    }

    // ---- 窗口内部类，管理标签页和侧边栏 ----

    private static final class ColonyWindow {
        private final ColonyCoreOpenResponsePacket packet;
        private final ViewContainer rightTabs = new ViewContainer();
        private final Map<String, View> openedTabs = new ConcurrentHashMap<>();
        private final UIElement sidebarContainer = new UIElement();
        private boolean sidebarCollapsed;

        private ColonyWindow(ColonyCoreOpenResponsePacket packet) {
            ColonyCoreScreenOpener.activeWindow = this;
            this.packet = packet;
            rightTabs.layout(layout -> {
                layout.flex(1);
                layout.heightPercent(100);
                layout.widthPercent(100);
            });
            rebuildSidebar();
            openDefaultTabs();
        }

        private void rebuildSidebar() {
            sidebarContainer.clearAllChildren();
            sidebarContainer.layout(layout -> {
                layout.width(sidebarCollapsed ? COLLAPSED_SIDEBAR_WIDTH : BUTTON_WIDTH);
                layout.heightPercent(100);
                layout.flexShrink(0);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.STRETCH);
            });
            sidebarContainer.addChild(sidebarHeader(sidebarCollapsed, this::toggleSidebar));
            if (!sidebarCollapsed) {
                sidebarContainer.addChild(scrollable(menuColumn(packet, this)));
            }
        }

        private void toggleSidebar() {
            sidebarCollapsed = !sidebarCollapsed;
            rebuildSidebar();
        }

        private void openDefaultTabs() {
            if (!packet.hasColony()) {
                openTab("create", "gui.xy2407_nsuk_addition.colony.menu_create", createPanel(packet));
            } else {
                openTab("info", "gui.xy2407_nsuk_addition.colony.menu_info", scrollable(contentPanel(packet)));
            }
        }

        private void openTab(String id, String titleKey, UIElement content) {
            View existing = openedTabs.get(id);
            if (existing != null && rightTabs.hasView(existing)) {
                rightTabs.selectView(existing);
                return;
            }
            BrowserTabView view = new BrowserTabView(titleKey, () -> openedTabs.remove(id));
            view.addChild(content);
            openedTabs.put(id, view);
            rightTabs.addView(view);
            rightTabs.selectView(view);
        }
    }

    // ---- 可关闭标签页视图 ----

    private static final class BrowserTabView extends View {
        private final Runnable removeListener;

        private BrowserTabView(String name, Runnable removeListener) {
            super(name, IGuiTexture.EMPTY);
            this.removeListener = removeListener;
            setCanRemove(true);
        }

        @Override
        public Tab createTab() {
            Tab tab = new Tab();
            tab.setText(Component.translatable(getName()));
            tab.addChild(new Button().setOnClick(event -> {
                if (event.button == 0) {
                    onClose();
                    event.stopPropagation();
                }
            }).noText().buttonStyle(buttonStyle -> buttonStyle.baseTexture(Icons.CLOSE)
                    .hoverTexture(Icons.CLOSE.copy().setColor(ColorPattern.LIGHT_GRAY.color))
                    .pressedTexture(Icons.CLOSE.copy().setColor(ColorPattern.GRAY.color))).layout(layout -> {
                layout.heightPercent(100);
                layout.setAspectRatio(1f);
            }));
            return tab;
        }

        @Override
        protected void onClose() {
            removeListener.run();
            removeSelf();
        }
    }
}
