package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.event.CityPlacementRestrictionHandler;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** 养殖控制箱受城市放置限制并注册为 POI。 */
@Mixin(value = CityPlacementRestrictionHandler.class, remap = false)
public class CityPlacementRestrictionHandlerMixin {

    @Inject(
            method = "isRestrictedBlock(Lnet/minecraft/world/level/block/Block;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void nsuk$isRestrictedBlock(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && block == ModBlocks.BREEDING_CONTROL_BOX.get()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "poiTypeForBlock(Lnet/minecraft/world/level/block/Block;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void nsuk$poiTypeForBlock(Block block, CallbackInfoReturnable<Optional<CityPoiType>> cir) {
        if (cir.getReturnValue().isEmpty() && block == ModBlocks.BREEDING_CONTROL_BOX.get()) {
            cir.setReturnValue(Optional.of(CityPoiType.OTHER));
        }
    }
}
