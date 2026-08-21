package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import com.xy2407.nsukaddition.server.city.VillageTourismService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 拦截 CitizenEntity 右键交互，商队 leader 优先打开商队交易 GUI。 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityCaravanTradeMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void nsuk$openCaravanTrade(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;
        CitizenEntity self = (CitizenEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return;
        if (player.distanceToSqr(self) > 64.0D) return;
        if (!VillageTourismService.isCaravanLeader(serverLevel, self.getUUID())) return;
        if (VillageTourismService.openCaravanTrade(serverLevel, serverPlayer, self)) {
            cir.setReturnValue(InteractionResult.sidedSuccess(self.level().isClientSide()));
        }
    }
}