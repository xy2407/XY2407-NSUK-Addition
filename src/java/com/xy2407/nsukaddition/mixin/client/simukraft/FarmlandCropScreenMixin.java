package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.farmland.FarmlandCropScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetCropPacket;
import com.xy2407.nsukaddition.client.farmland.CropIconElement;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** 修改 FarmlandCropScreen，重写界面以添加作物图标显示和选中标记。 */
@Mixin(FarmlandCropScreen.class)
@OnlyIn(Dist.CLIENT)
public class FarmlandCropScreenMixin {

    @Overwrite
    @SuppressWarnings("deprecation")
    private static ModularUI createUi(FarmlandBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(8);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));
        root.addChild(topButton("gui.button.back", () -> back(packet.boxPos())));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(90);
            layout.maxWidth(240);
            layout.maxHeight((int) (screenHeight * 0.85));
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.paddingAll(10);
            layout.gapAll(5);
        }).addClass("simukraft_panel");

        panel.addChild(label(Component.translatable("gui.simukraft.farmland_box.select_crop_title"), Horizontal.CENTER, 0xFFFFFF, 16));

        UIElement cropList = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(5);
        });
        for (FarmCrop crop : FarmCrop.values()) {
            boolean selected = crop.id().equals(packet.cropId());
            Component text = selected
                    ? Component.translatable("gui.simukraft.farmland_box.crop_selected", Component.translatable(crop.translationKey()))
                    : Component.translatable(crop.translationKey());
            cropList.addChild(cropButtonWithIcon(text, packet.boxPos(), crop));
        }

        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
            layout.flexShrink(1);
        });
        scroller.addScrollViewChild(cropList);
        panel.addChild(scroller);

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement cropButtonWithIcon(Component text, BlockPos boxPos, FarmCrop crop) {
        UIElement slot = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(22);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
        });
        slot.addChild(new CropIconElement(new ItemStack(crop.seed())));
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> PacketDistributor.sendToServer(new FarmlandBoxSetCropPacket(boxPos, crop.id())));
        button.layout(layout -> {
            layout.flexGrow(1);
            layout.height(22);
        });
        slot.addChild(button);
        return slot;
    }

    private static Button topButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(5);
            layout.top(5);
            layout.width(50);
            layout.height(22);
        });
        return button;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(true)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static void back(BlockPos boxPos) {
        PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(boxPos));
    }
}
