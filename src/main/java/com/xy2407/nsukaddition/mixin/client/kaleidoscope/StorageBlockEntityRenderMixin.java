package com.xy2407.nsukaddition.mixin.client.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.client.render.block.StorageBlockEntityRender;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.vinery.core.block.WineBottleBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 修复 StorageBlockEntityRender#renderStack 中 Vinery 酒瓶因 FAKE_MODEL 渲染为空的问题。 */
@Mixin(StorageBlockEntityRender.class)
public class StorageBlockEntityRenderMixin {

    @ModifyArg(
            method = "renderStack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    remap = true
            ),
            index = 0,
            remap = false
    )
    private BlockState nsuk$fixVineryBottleRender(BlockState state) {
        if (state.hasProperty(WineBottleBlock.FAKE_MODEL)) {
            return state.setValue(WineBottleBlock.FAKE_MODEL, false);
        }
        return state;
    }
}
