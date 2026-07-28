package com.xy2407.nsukaddition.mixin.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 BarrelBlockEntity 的 output 字段供兼容逻辑使用。 */
@Mixin(BarrelBlockEntity.class)
public interface BarrelBlockEntityAccessor {

    @Accessor("output")
    ItemStackHandler nsuk$getOutput();
}
