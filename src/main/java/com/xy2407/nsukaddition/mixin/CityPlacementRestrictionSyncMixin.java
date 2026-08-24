package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.event.CityPlacementRestrictionHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 城市放置限制拦截后主动刷新玩家背包显示，避免返还物品客户端不立即渲染。 */
@Mixin(value = CityPlacementRestrictionHandler.class, remap = false)
public class CityPlacementRestrictionSyncMixin {

    @Inject(method = "onRightClickBlock", at = @At("RETURN"), remap = false)
    private static void nsuk$syncAfterRightClickCancel(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    @Inject(method = "onBlockPlaced", at = @At("RETURN"), remap = false)
    private static void nsuk$syncAfterPlaceCancel(BlockEvent.EntityPlaceEvent event, CallbackInfo ci) {
        if (event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            player.inventoryMenu.broadcastChanges();
        }
    }
}