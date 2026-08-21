package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.industrial.FlowerBonemealHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 骨粉对花（#minecraft:flowers 标签，排除 leaves）右键：原方块不变，额外掉落一个花朵物品。
 * 原版仅两格高花(TallFlowerBlock)骨粉会掉落自身物品，一格高花无效；此 Mixin 统一处理。
 */
@Mixin(BoneMealItem.class)
public abstract class BoneMealFlowerDropMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$flowerBonemealDrop(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            context.getItemInHand().shrink(1);
            FlowerBonemealHelper.bonemealFlowerDrop(serverLevel, pos);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide()));
    }
}
