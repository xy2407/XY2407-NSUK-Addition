package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.menu.CitizenEquipmentMenuProvider;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 玩家手持盔甲类物品右键市民时打开装备菜单，并拦截原版对市民的盔甲穿戴/替换逻辑。 */
@SuppressWarnings("null")
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityInteractionMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$openEquipmentOnArmorInteract(Player player, InteractionHand hand,
                                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        CitizenEntity citizen = (CitizenEntity) (Object) this;
        if (citizen.isRemoved() || !citizen.isAlive()) return;
        if (isCommercialWorker(citizen)) return;
        ItemStack held = player.getItemInHand(hand);
        if (!isArmorItem(held)) return;
        if (player.distanceToSqr(citizen) > 64.0D) return;
        if (CitizenEquipmentMenuProvider.open(serverPlayer, citizen)) {
            cir.setReturnValue(InteractionResult.sidedSuccess(player.level().isClientSide()));
        }
    }

    private static boolean isArmorItem(ItemStack stack) {
        return stack.is(ItemTags.HEAD_ARMOR)
                || stack.is(ItemTags.CHEST_ARMOR)
                || stack.is(ItemTags.LEG_ARMOR)
                || stack.is(ItemTags.FOOT_ARMOR);
    }

    private static boolean isCommercialWorker(CitizenEntity citizen) {
        String job = citizen.getJob();
        return "commercial".equals(job) || "commercial_worker".equals(job);
    }
}
