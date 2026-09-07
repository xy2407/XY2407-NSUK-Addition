package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.farmland.FarmlandCropScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetCropPacket;
import com.xy2407.nsukaddition.client.farmland.CropIconElement;
import com.xy2407.nsukaddition.common.farmland.GlowBerryHelper;
import com.xy2407.nsukaddition.common.farmland.JungleVineCropHelper;
import com.xy2407.nsukaddition.common.farmland.MushroomCropHelper;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        // 每行做成一张卡片：产出贴图 + 种子贴图 + 额外材料贴图 + 选择按钮
        UIElement slot = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(22);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
        }).addClass("simukraft_panel");

        ItemStack produce = cropProduce(crop);
        CropIconElement produceIcon = new CropIconElement(produce);
        produceIcon.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (!produce.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(
                        Screen.getTooltipFromItem(Minecraft.getInstance(), produce),
                        null, null, produce);
            }
        });
        slot.addChild(produceIcon);

        // 第一个(作物)图标与后方的种子/材料之间加竖直分割线，明确区分作物与种植材料
        slot.addChild(divider());

        ItemStack seedStack = new ItemStack(crop.seed());
        CropIconElement seedIcon = new CropIconElement(seedStack);
        seedIcon.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (!seedStack.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(
                        Screen.getTooltipFromItem(Minecraft.getInstance(), seedStack),
                        null, null, seedStack);
            }
        });
        slot.addChild(seedIcon);

        // 需要特殊土壤/木头的作物：追加材料图标，悬停显示原版 tooltip
        for (ItemStack extra : specialMaterials(crop)) {
            slot.addChild(materialIcon(extra));
        }

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

    /** 作物产出贴图：优先用"产物物品映射表"取果实图标，再回退方形块/实体；最后才回退种子。 */
    private static ItemStack cropProduce(FarmCrop crop) {
        ItemStack produce = productItem(crop);
        if (!produce.isEmpty()) {
            return produce;
        }
        Block pb = crop.produceBlock();
        if (pb != null) {
            ItemStack s = new ItemStack(pb.asItem());
            if (!s.isEmpty()) {
                return s;
            }
        }
        String n = crop.name().toUpperCase(Locale.ROOT);
        String id = switch (n) {
            case "WHEAT" -> "minecraft:wheat";
            case "CARROTS" -> "minecraft:carrot";
            case "POTATOES" -> "minecraft:potato";
            case "BEETROOTS" -> "minecraft:beetroot";
            default -> null;
        };
        if (id != null) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Item it = BuiltInRegistries.ITEM.get(rl);
                if (it != null) {
                    return new ItemStack(it);
                }
            }
        }
        // modded 作物（produceBlock 通常为空）：用作物方块对应物品作为"作物"图标，避免落到种子图标
        Block plant = crop.plantBlock();
        if (plant != null) {
            ItemStack s = new ItemStack(plant.asItem());
            if (!s.isEmpty()) {
                return s;
            }
        }
        return new ItemStack(crop.seed());
    }

    /** 模组作物 → 产物(果实)物品 ID；无方块物品的作物(bush/瓜/作物方块)用其收获物品作图标。 */
    private static String productItemId(String cropId) {
        return switch (cropId) {
            case "culturaldelights_cucumber" -> "culturaldelights:cucumber";
            case "culturaldelights_eggplant" -> "culturaldelights:eggplant";
            case "culturaldelights_corn" -> "culturaldelights:corn_cob";
            case "farm_and_charm_barley" -> "farm_and_charm:barley";
            case "farm_and_charm_oat" -> "farm_and_charm:oat";
            case "farm_and_charm_strawberry" -> "farm_and_charm:strawberry";
            case "farmersdelight_cabbage" -> "farmersdelight:cabbage";
            case "farmersdelight_rice" -> "farmersdelight:rice_panicles";
            case "farmersdelight_onion" -> "farmersdelight:onion";
            case "kaleidoscope_cookery_tomato" -> "kaleidoscope_cookery:tomato";
            case "kaleidoscope_cookery_chili" -> "kaleidoscope_cookery:chili";
            case "kaleidoscope_cookery_lettuce" -> "kaleidoscope_cookery:lettuce";
            case "kt_grape" -> "kaleidoscope_tavern:grape";
            case "kt_ice_grape" -> "kaleidoscope_tavern:ice_grape";
            case "kt_gold_grape" -> "kaleidoscope_tavern:gold_grape";
            case "vinery_red_grape" -> "vinery:red_grape";
            case "vinery_white_grape" -> "vinery:white_grape";
            case "vinery_savanna_grape_red" -> "vinery:savanna_grapes_red";
            case "vinery_savanna_grape_white" -> "vinery:savanna_grapes_white";
            case "vinery_taiga_grape_red" -> "vinery:taiga_grapes_red";
            case "vinery_taiga_grape_white" -> "vinery:taiga_grapes_white";
            case "vinery_jungle_grape_red" -> "vinery:jungle_grapes_red";
            case "vinery_jungle_grape_white" -> "vinery:jungle_grapes_white";
            default -> null;
        };
    }

    /** 按映射取作物产物图标；rice 的"水稻穗"若是方块无物品则回退到稻米。 */
    private static ItemStack productItem(FarmCrop crop) {
        String id = productItemId(crop.id());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = itemOrBlockItem(id);
        if (s.isEmpty() && "farmersdelight_rice".equals(crop.id())) {
            s = itemOrBlockItem("farmersdelight:rice");
        }
        return s;
    }

    /** 先当物品解析，无对应物品时当作方块取其方块物品。 */
    private static ItemStack itemOrBlockItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return new ItemStack(item);
        }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        if (block != null) {
            Item bi = block.asItem();
            if (bi != null && bi != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(bi);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 需要特殊土壤/木头的作物对应额外材料，如蘑菇需沃土、丛林木藤蔓需原木。 */
    private static List<ItemStack> specialMaterials(FarmCrop crop) {
        List<ItemStack> mats = new ArrayList<>();
        if (MushroomCropHelper.isMushroomCrop(crop)) {
            addItem(mats, "farmersdelight:rich_soil");
        }
        if (GlowBerryHelper.isGlowBerry(crop) || JungleVineCropHelper.isCocoa(crop)
                || JungleVineCropHelper.isJungleGrape(crop)) {
            addItem(mats, "minecraft:jungle_log");
        }
        return mats;
    }

    private static void addItem(List<ItemStack> mats, String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item != null) mats.add(new ItemStack(item));
    }

    /** 通用材料图标（16x16）：ItemStackTexture 自绘，悬停用该物品原版 tooltip。 */
    private static UIElement materialIcon(ItemStack stack) {
        return new UIElement().layout(l -> { l.width(16); l.height(16); l.flexShrink(0); })
                .style(s -> s.backgroundTexture(new ItemStackTexture(stack)))
                .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                    if (!stack.isEmpty()) {
                        event.hoverTooltips = new HoverTooltips(
                                Screen.getTooltipFromItem(Minecraft.getInstance(), stack),
                                null, null, stack);
                    }
                });
    }

    /** 竖直分割线：隔开作物贴图与材料贴图，用 ColorRectTexture 绘制避免自定义元素。 */
    private static UIElement divider() {
        return new UIElement()
                .layout(l -> { l.width(2); l.height(16); l.flexShrink(0); })
                .style(s -> s.backgroundTexture(new ColorRectTexture(0xAFFFFFFF)));
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
