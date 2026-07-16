package com.xy2407.nsukaddition.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.client.vein.RichIronOverlayRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 ItemRenderer，为带富矿标记的物品叠加矿石小图标。 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("RETURN")
    )
    private void nsukaddition$renderItemOverlay(ItemStack stack, ItemDisplayContext displayContext,
                                                 boolean leftHand, PoseStack poseStack,
                                                 MultiBufferSource buffer, int combinedLight,
                                                 int combinedOverlay, BakedModel model,
                                                 CallbackInfo ci) {
        if (!RichIronOverlayRenderer.shouldRender(stack)) return;
        RichIronOverlayRenderer.render((ItemRenderer) (Object) this, stack, displayContext,
                leftHand, poseStack, buffer, combinedLight, combinedOverlay);
    }
}
