package com.xy2407.nsukaddition.mixin;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** 修改 Animal#setInLove，实现羊无毛时喂食长毛而非交配。 */
@Mixin(Animal.class)
public class SheepWoolRegrowMixin {

    @Inject(method = "setInLove", at = @At("HEAD"), cancellable = true)
    private void xy2407$onSetInLove(@Nullable Player player, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;
        if (!(self instanceof Sheep sheep)) return;

        if (!sheep.isSheared()) return;

        sheep.setSheared(false);
        sheep.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
        ci.cancel();
    }
}
