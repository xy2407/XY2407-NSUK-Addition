package com.xy2407.nsukaddition.common.item;

import com.xy2407.nsukaddition.common.breeding.BreedingWorkService;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 拦截玩家对实体的交互,使空捕获器在骑乘等原版交互之前完成捕获。 */
public final class EntityCaptureInteractHandler {

    private EntityCaptureInteractHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EntityCaptureItem item)) {
            return;
        }
        if (EntityCaptureItem.getEntityType(stack) != null) {
            return;
        }
        if (!(event.getTarget() instanceof Mob mob)) {
            return;
        }
        Player player = event.getEntity();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.level().isClientSide()) {
            if (BreedingWorkService.isBaseEntity(mob)) {
                boolean moved = BreedingWorkService.baseToCapture((net.minecraft.server.level.ServerLevel) player.level(),
                        player, event.getHand(), mob);
                event.setCancellationResult(moved ? InteractionResult.SUCCESS : InteractionResult.PASS);
            } else {
                item.captureFromHand(player, mob, event.getHand());
            }
        }
    }
}
