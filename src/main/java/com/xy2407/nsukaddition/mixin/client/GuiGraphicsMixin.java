package com.xy2407.nsukaddition.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 生物捕获器图标右下角绘制内部实体条目标识数(仿原版堆叠数字)。 */
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    @Inject(method = "renderItemDecorations", at = @At("TAIL"))
    private void nsukaddition$drawCaptureCount(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        if (!(stack.getItem() instanceof EntityCaptureItem)) {
            return;
        }
        int count = EntityCaptureItem.getEntryCount(stack);
        if (count <= 0) {
            return;
        }
        String text = String.valueOf(count);
        int width = font.width(text);
        GuiGraphics self = (GuiGraphics) (Object) this;
        PoseStack pose = self.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 200.0F);
        self.drawString(font, text, x + 16 - width - 1, y + 7, 0xFFFFFF, true);
        pose.popPose();
    }
}