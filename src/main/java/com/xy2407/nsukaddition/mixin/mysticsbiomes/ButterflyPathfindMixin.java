package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.Butterfly;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 优化蝴蝶寻路：将飞向花/巢的寻路节点上限由 10 倍降回 1 倍，减少寻路开销。 */
@Mixin(Butterfly.class)
public abstract class ButterflyPathfindMixin {

    @Redirect(method = "pathfindDirectlyTowards",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;setMaxVisitedNodesMultiplier(F)V"))
    private void xy2407$capPathfinding(PathNavigation navigation, float multiplier) {
        navigation.setMaxVisitedNodesMultiplier(1.0f);
    }
}