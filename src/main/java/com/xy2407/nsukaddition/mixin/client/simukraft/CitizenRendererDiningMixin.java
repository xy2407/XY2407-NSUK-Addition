package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.client.network.DiningOrderClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在市民头顶渲染菜品图标，沿用原版铭牌的相机朝向与缩放方式。 */
@Mixin(CitizenRenderer.class)
public class CitizenRendererDiningMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void nsuk$renderDiningBubble(CitizenEntity entity, float entityYaw, float partialTick,
                                          PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight, CallbackInfo ci) {
        if (entity == null || !entity.isAlive()) return;

        String itemId = DiningOrderClientHandler.getOrderedItem(entity.getUUID());
        if (itemId == null) return;

        ItemStack stack = parseItemStack(itemId);
        if (stack.isEmpty()) return;

        float height = entity.getBbHeight() + 0.7F;

        poseStack.pushPose();
        poseStack.translate(0.0F, height, 0.0F);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025F;
        poseStack.scale(s, -s, s);
        poseStack.translate(7, -4, 0);

        poseStack.pushPose();
        float iconScale = 14;
        poseStack.scale(iconScale, -iconScale, iconScale);
        poseStack.translate(-0.5, -0.5, 0);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), 0);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static ItemStack parseItemStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(id);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }
}
