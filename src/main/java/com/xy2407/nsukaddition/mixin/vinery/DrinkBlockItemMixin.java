package com.xy2407.nsukaddition.mixin.vinery;

import com.github.ysbbbbbb.kaleidoscopetavern.init.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.vinery.core.item.DrinkBlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vinery 酒瓶蹲下右键放置时替换为 Kaleidoscope 对应酒瓶方块。 */
@Mixin(DrinkBlockItem.class)
public abstract class DrinkBlockItemMixin extends BlockItem {

    @Shadow
    private DrinkBlockItem.BottleSize bottleSize;

    private DrinkBlockItemMixin(Block block, Properties properties) {
        super(block, properties);
    }

    @Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
    private void nsuk$placeKaleidoscopeBottle(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        if (context.getPlayer() == null || !context.getPlayer().isCrouching()) {
            return;
        }

        Block targetBlock;
        if (bottleSize == DrinkBlockItem.BottleSize.BIG) {
            targetBlock = ModBlocks.BRANDY.get();
        } else {
            targetBlock = ModBlocks.WINE.get();
        }

        BlockState targetState = targetBlock.getStateForPlacement(context);
        BlockBehaviourInvoker invoker = (BlockBehaviourInvoker) targetBlock;
        if (targetState != null && context.getLevel().getBlockState(context.getClickedPos()).canBeReplaced()
                && invoker.invokeCanSurvive(targetState, context.getLevel(), context.getClickedPos())) {
            cir.setReturnValue(targetState);
        } else {
            cir.setReturnValue(null);
        }
    }
}
