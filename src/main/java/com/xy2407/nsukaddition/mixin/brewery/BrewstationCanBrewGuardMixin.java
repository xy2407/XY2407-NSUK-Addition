package com.xy2407.nsukaddition.mixin.brewery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.brewery.core.block.entity.BrewstationBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brewery 酿造站多方块组件装配保障与 NPE 防御：
 * 1. tick HEAD 自动装配：components 为空时按朝向扫描周围方块补齐组件，让酿造站能正常工作。
 * 2. canBrew HEAD 防御：组件缺失或烤炉被移除时返回 false，避免 getBlockState(null) NPE 崩溃。
 * 方块通过原版注册表查询，避免 architectury RegistrySupplier 类型依赖。
 */
@Mixin(BrewstationBlockEntity.class)
public class BrewstationCanBrewGuardMixin {

    private static final String BREWERY_MOD_ID = "brewery";

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void xy2407$autoAssembleComponents(Level level, BlockPos pos, BlockState state,
                                                      BrewstationBlockEntity self, CallbackInfo ci) {
        if (level.isClientSide) return;
        if (!self.getComponents().isEmpty()) return;

        Block brewTimer = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(BREWERY_MOD_ID, "brew_timer"));
        Block brewWhistle = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(BREWERY_MOD_ID, "brew_whistle"));
        Block brewOven = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(BREWERY_MOD_ID, "brew_oven"));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos backPos = pos.relative(facing.getOpposite());
            BlockPos sidePos = pos.relative(facing.getCounterClockWise());
            BlockPos diagonalPos = sidePos.relative(facing.getOpposite());

            if (level.getBlockState(backPos).getBlock() == brewTimer
                    && level.getBlockState(sidePos).getBlock() == brewWhistle
                    && level.getBlockState(diagonalPos).getBlock() == brewOven) {
                self.setComponents(pos, backPos, sidePos, diagonalPos);
                self.setChanged();
                return;
            }
        }
        ci.cancel();
    }

    @Inject(method = "canBrew(Lnet/minecraft/world/item/crafting/Recipe;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void xy2407$guardCanBrewNullComponents(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        BrewstationBlockEntity self = (BrewstationBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getComponents().isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        Block brewOven = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(BREWERY_MOD_ID, "brew_oven"));
        if (self.getComponents().stream().noneMatch(p ->
                self.getLevel().getBlockState(p).getBlock() == brewOven)) {
            cir.setReturnValue(false);
        }
    }
}
