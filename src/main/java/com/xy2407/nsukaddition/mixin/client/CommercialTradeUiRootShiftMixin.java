package com.xy2407.nsukaddition.mixin.client;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import common.cn.kafei.simukraft.commercial.CommercialTradeUiRoot;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 商业/商队交易：shift 批量时按剩余库存/容量计算数量，不足64时一次性买或卖完剩余。 */
@Mixin(CommercialTradeUiRoot.class)
public abstract class CommercialTradeUiRootShiftMixin {

    @Invoker("selectedOffer")
    protected abstract CommercialTradeOpenResponsePacket.OfferEntry invokeSelectedOffer();

    @Invoker("canTrade")
    protected abstract boolean invokeCanTrade(CommercialTradeOpenResponsePacket.OfferEntry offer);

    @Invoker("tradeSelected")
    protected abstract void invokeTradeSelected(boolean quickMove, int count);

    @Inject(method = "onResultSlotMouseDown", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukaddition$shiftBulkOnResult(UIEvent event, CallbackInfo ci) {
        CommercialTradeOpenResponsePacket.OfferEntry offer = invokeSelectedOffer();
        if (event.button != 0 || offer == null || !invokeCanTrade(offer)) {
            return;
        }
        boolean isRetail = !offer.result().isEmpty() && offer.result().get(0).count() == 1;
        int count = (Screen.hasShiftDown() && isRetail) ? nsukShiftCount(offer) : 1;
        invokeTradeSelected(true, count);
        event.stopImmediatePropagation();
        ci.cancel();
    }

    @Unique
    private static int nsukShiftCount(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        int bulk = 64;
        boolean leaving = offer.result().stream()
                .anyMatch(r -> !isMoney(r) && offer.stockItem().equals(r.itemId()));
        if (leaving) {
            bulk = Math.min(bulk, offer.currentStock());
        } else {
            int room = offer.maxStock() - offer.currentStock();
            bulk = Math.min(bulk, Math.max(0, room));
            bulk = Math.min(bulk, playerHeld(offer.stockItem()));
        }
        return Math.max(1, bulk);
    }

    @Unique
    private static int playerHeld(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Unique
    private static boolean isMoney(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        return "money".equalsIgnoreCase(resource.type());
    }
}