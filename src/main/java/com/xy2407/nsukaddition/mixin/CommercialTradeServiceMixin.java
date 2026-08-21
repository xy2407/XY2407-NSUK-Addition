package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.capture.CaptureBuySupport;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import com.xy2407.nsukaddition.common.registry.ModEntityItems;
import common.cn.kafei.simukraft.commercial.CommercialOffer;
import common.cn.kafei.simukraft.commercial.CommercialResource;
import common.cn.kafei.simukraft.commercial.CommercialTaxService;
import common.cn.kafei.simukraft.commercial.CommercialTradeService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 商业贸易中捕获器按内部实体条目数结算；并屏蔽普通NPC购买时的收入记录。 */
@Mixin(CommercialTradeService.class)
public class CommercialTradeServiceMixin {

    @Redirect(
            method = "executeNpcOffer",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/commercial/CommercialTaxService;recordShopIncome(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;D)V"),
            remap = false
    )
    private static void nsuk$disableNpcIncome(ServerLevel level, UUID cityId, double amount) {
    }

    @Inject(method = "countPlayerItems", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$countCaptureEntries(ServerPlayer player, Item item, CallbackInfoReturnable<Integer> cir) {
        if (item != ModEntityItems.ENTITY_CAPTURE.get()) {
            return;
        }
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += EntityCaptureItem.getEntryCount(stack);
            }
        }
        cir.setReturnValue(count);
    }

    @Inject(method = "removePlayerItems", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$removeCaptureEntries(ServerPlayer player, Item item, int amount, CallbackInfo ci) {
        if (item != ModEntityItems.ENTITY_CAPTURE.get()) {
            return;
        }
        int remaining = Math.max(0, amount);
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            remaining -= EntityCaptureItem.removeEntries(stack, remaining);
        }
        ci.cancel();
    }

    @Inject(method = "resultItemStacks", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$captureResultStacks(CommercialOffer offer, int times, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (offer == null) {
            return;
        }
        CaptureBuySupport.Spec spec = CaptureBuySupport.spec(offer.id());
        if (spec == null) {
            return;
        }
        List<ItemStack> result = new ArrayList<>();
        for (CommercialResource resource : offer.result()) {
            if (resource.type() != CommercialResource.Type.ITEM) {
                continue;
            }
            if (resource.item() == ModEntityItems.ENTITY_CAPTURE.get()) {
                int amount = resource.countFor(times);
                ItemStack seed = new ItemStack(ModEntityItems.ENTITY_CAPTURE.get());
                result.add(EntityCaptureItem.createCapture(seed, spec.type(), spec.baby(), amount));
            } else {
                result.add(resource.stack(times));
            }
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "giveItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$giveCaptureMerged(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof EntityCaptureItem)) {
            return;
        }
        EntityType<?> type = EntityCaptureItem.getEntityType(stack);
        if (type == null) {
            return;
        }
        boolean baby = EntityCaptureItem.isBaby(stack);
        int count = EntityCaptureItem.getEntryCount(stack);
        int remaining = EntityCaptureItem.distributeCapture(
                player.getInventory().items, new ItemStack(stack.getItem()), type, baby, count);
        ci.cancel();
        if (remaining > 0) {
            ItemStack leftover = EntityCaptureItem.createCapture(new ItemStack(stack.getItem()), type, baby, remaining);
            if (!player.addItem(leftover) && !leftover.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        player.serverLevel(), player.getX(), player.getY(), player.getZ(), leftover, 0, 0, 0);
                drop.setNoPickUpDelay();
                player.serverLevel().addFreshEntity(drop);
            }
        }
    }
}