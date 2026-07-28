package com.xy2407.nsukaddition.mixin.client.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.client.render.block.BarCabinetBlockEntityRender;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.vinery.core.block.WineBottleBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 让 Kaleidoscope 酒柜渲染器正确显示 Vinery 酒瓶模型（关闭 FAKE_MODEL 防止渲染为空）。 */
@Mixin(BarCabinetBlockEntityRender.class)
public class BarCabinetBlockEntityRenderMixin {

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    remap = true
            ),
            index = 0,
            remap = false
    )
    private BlockState nsuk$showVineryBottleModel(BlockState state) {
        if (state.hasProperty(WineBottleBlock.FAKE_MODEL)) {
            return state.setValue(WineBottleBlock.FAKE_MODEL, false);
        }
        return state;
    }
}
