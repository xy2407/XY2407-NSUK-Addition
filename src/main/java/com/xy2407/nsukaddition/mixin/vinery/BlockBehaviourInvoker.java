package com.xy2407.nsukaddition.mixin.vinery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 BlockBehaviour#canSurvive 为公共访问，供 DrinkBlockItemMixin 调用。 */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.class)
public interface BlockBehaviourInvoker {
    @Invoker("canSurvive")
    boolean invokeCanSurvive(BlockState state, LevelReader level, BlockPos pos);
}
