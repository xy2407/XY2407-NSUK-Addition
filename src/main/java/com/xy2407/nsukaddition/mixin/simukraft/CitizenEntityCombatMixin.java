package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.combat.CitizenCombatService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NPC 战斗混入：tick 注入战斗 AI，hurt 注入三击一格挡盾牌逻辑。
 */
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityCombatMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void nsukaddition$onTick(CallbackInfo ci) {
        CitizenEntity self = (CitizenEntity) (Object) this;
        if (!self.level().isClientSide() && self.level() instanceof ServerLevel serverLevel) {
            CitizenCombatService.tickCombat(serverLevel, self);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void nsukaddition$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        CitizenEntity self = (CitizenEntity) (Object) this;
        if (!self.level().isClientSide()) {
            CitizenCombatService.onNpcHurt(self, source);
        }
    }
}