package com.xy2407.nsukaddition.mixin.client.simukraft;

import com.xy2407.nsukaddition.common.foreigntrade.TradeItemResolver;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket.ResourceEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 商队交易界面资源图标：动物(entity类型)条目渲染为生物捕获器，避免无效item解析成屏障方块。 */
@Mixin(targets = "client.cn.kafei.simukraft.client.commercial.CommercialClientTradeUi", remap = false)
public abstract class CommercialClientTradeUiMixin {

    @Inject(method = "resourceStack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$entityCaptureResource(ResourceEntry resource, CallbackInfoReturnable<ItemStack> cir) {
        if (resource == null || "money".equalsIgnoreCase(resource.type())) {
            return;
        }
        String itemId = resource.itemId();
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        // 正常物品：走原逻辑
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            return;
        }
        // 无效物品：尝试按动物实体渲染为生物捕获器（商队的动物商品以实体id作为itemId）
        ItemStack capture = TradeItemResolver.buildDisplay(itemId, "animal", Math.max(1, resource.count()));
        if (capture == null || capture.isEmpty()) {
            return;
        }
        cir.setReturnValue(capture);
    }
}