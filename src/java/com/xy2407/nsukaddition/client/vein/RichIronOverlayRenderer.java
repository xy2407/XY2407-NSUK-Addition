package com.xy2407.nsukaddition.client.vein;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.server.vein.OreVeinDropHandler;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 富矿物品的 GUI 叠加层渲染器，在物品图标右下角绘制对应的矿物缩略图。 */
@OnlyIn(Dist.CLIENT)
public final class RichIronOverlayRenderer {

    private static final ThreadLocal<Boolean> RENDERING_OVERLAY = ThreadLocal.withInitial(() -> false);

    private RichIronOverlayRenderer() {}

    public static boolean shouldRender(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.has(DataComponents.CUSTOM_DATA)
                && getRichOreType(stack) != null;
    }

    public static void render(ItemRenderer renderer, ItemStack original,
                              ItemDisplayContext displayContext, boolean leftHand,
                              PoseStack poseStack, MultiBufferSource buffer,
                              int light, int combinedOverlay) {
        if (RENDERING_OVERLAY.get()) return;

        if (displayContext != ItemDisplayContext.GUI) return;

        Item overlayItem = getOverlayItem(original);
        if (overlayItem == null) return;

        ItemStack overlayStack = new ItemStack(overlayItem);
        BakedModel model = renderer.getModel(overlayStack, null, null, 0);

        RENDERING_OVERLAY.set(true);
        poseStack.pushPose();
        try {
            applyOverlayTransform(poseStack);

            renderer.render(overlayStack, ItemDisplayContext.GUI, false, poseStack, buffer,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        } finally {
            poseStack.popPose();
            RENDERING_OVERLAY.set(false);
        }
    }

    private static String getRichOreType(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        return tag.contains(OreVeinDropHandler.RICH_ORE_TAG) ? tag.getString(OreVeinDropHandler.RICH_ORE_TAG) : null;
    }

    private static Item getOverlayItem(ItemStack original) {
        String typeId = getRichOreType(original);
        if (typeId == null) return null;
        return switch (typeId) {
            case "coal" -> Items.COAL;
            case "copper" -> Items.COPPER_INGOT;
            case "iron" -> Items.IRON_INGOT;
            case "gold" -> Items.GOLD_INGOT;
            case "redstone" -> Items.REDSTONE;
            case "lapis_lazuli" -> Items.LAPIS_LAZULI;
            case "diamond" -> Items.DIAMOND;
            case "emerald" -> Items.EMERALD;
            case "quartz" -> Items.QUARTZ;
            case "nether_gold" -> Items.GOLD_INGOT;
            case "ancient_debris" -> Items.NETHERITE_SCRAP;
            default -> null;
        };
    }

    private static void applyOverlayTransform(PoseStack poseStack) {
        poseStack.scale(0.525f, 0.525f, 0.525f);

        poseStack.translate(-0.7f, 0.5f, 24f);
    }
}
