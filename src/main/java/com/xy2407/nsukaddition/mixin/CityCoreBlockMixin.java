package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityCoreMoveService;
import common.cn.kafei.simukraft.block.CityCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 CityCoreBlock.onRemove，迁移时跳过方块保护逻辑。 */
@Mixin(value = CityCoreBlock.class, remap = false)
public class CityCoreBlockMixin {

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston, CallbackInfo ci) {
        if (CityCoreMoveService.isMovingFrom(pos)) {
            ci.cancel();
        }
    }
}
