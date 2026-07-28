package com.xy2407.nsukaddition.mixin.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity;
import com.xy2407.nsukaddition.common.compat.vinerykaleidoscope.VineryKaleidoscopeCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.satisfy.vinery.core.block.WineBottleBlock;
import net.satisfy.vinery.core.block.entity.StorageBlockEntity;
import net.satisfy.vinery.core.item.DrinkBlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让 Kaleidoscope 酒桶能够正确放置 Vinery 酒瓶作为酿造产物。 */
@Mixin(BarrelBlockEntity.class)
public class BarrelBlockEntityMixin {

    @Inject(method = "placeBlockResult", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$handleVineryBottleResult(Level level, BlockPos below, BlockState belowState, BlockItem result, CallbackInfo ci) {
        if (!VineryKaleidoscopeCompat.isVineryBottleItem(result)) {
            return;
        }
        DrinkBlockItem item = (DrinkBlockItem) result;
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(WineBottleBlock.FAKE_MODEL)) {
            state = state.setValue(WineBottleBlock.FAKE_MODEL, false);
        }
        if (state.hasProperty(BlockStateProperties.FACING) && belowState.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, belowState.getValue(BlockStateProperties.FACING));
        }
        level.setBlockAndUpdate(below, state);
        BlockEntity blockEntity = level.getBlockEntity(below);
        if (blockEntity instanceof StorageBlockEntity storageEntity) {
            storageEntity.setStack(0, new ItemStack(result));
        }
        ci.cancel();
    }
}
