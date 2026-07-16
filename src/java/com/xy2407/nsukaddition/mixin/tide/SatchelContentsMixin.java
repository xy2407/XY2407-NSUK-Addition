package com.xy2407.nsukaddition.mixin.tide;

import com.li64.tide.data.item.SatchelContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 修改 SatchelContents.Mutable，将鱼篓最大堆叠数从六十四提升至五百一十二。 */
@Mixin(SatchelContents.Mutable.class)
public class SatchelContentsMixin {

    private static final int NEW_MAX_STACKS = 512;

    @ModifyConstant(method = "tryInsert", constant = @Constant(intValue = 64), remap = false)
    private int modifyMaxStacks(int original) {
        return NEW_MAX_STACKS;
    }
}
