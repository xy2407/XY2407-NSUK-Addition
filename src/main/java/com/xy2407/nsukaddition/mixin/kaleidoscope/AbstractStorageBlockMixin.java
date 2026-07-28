package com.xy2407.nsukaddition.mixin.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.block.AbstractStorageBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.deco.StorageBlockEntity;
import com.xy2407.nsukaddition.common.compat.vinerykaleidoscope.VineryKaleidoscopeCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Kaleidoscope 通用存储架（圆周/倾斜/单体/窖藏酒柜）额外接受 Vinery 酒瓶。 */
@Mixin(AbstractStorageBlock.class)
public class AbstractStorageBlockMixin {

    @Inject(method = "putOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$acceptVineryBottle(Level level, BlockPos pos, Player player,
                                          StorageBlockEntity storage, int clickedSlot,
                                          CallbackInfoReturnable<ItemInteractionResult> cir) {
        ItemStack handItem = player.getMainHandItem();
        if (!VineryKaleidoscopeCompat.isVineryBottle(handItem)) {
            return;
        }
        ItemStackHandler items = storage.getItems();
        if (items.getStackInSlot(clickedSlot).isEmpty()) {
            items.setStackInSlot(clickedSlot, handItem.split(1));
            storage.refresh();
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS);
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        } else {
            cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
