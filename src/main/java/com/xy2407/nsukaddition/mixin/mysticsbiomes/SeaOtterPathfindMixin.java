package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.SeaOtter;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 优化海獭寻路：将两栖寻路节点上限由 10 倍降回 1 倍，显著降低寻路开销。 */
@Mixin(SeaOtter.class)
public abstract class SeaOtterPathfindMixin {

    @Redirect(method = "pathfindDirectlyTowards",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;setMaxVisitedNodesMultiplier(F)V"))
    private void xy2407$capPathfinding(PathNavigation navigation, float multiplier) {
        navigation.setMaxVisitedNodesMultiplier(1.0f);
    }
}