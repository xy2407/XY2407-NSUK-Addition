package com.xy2407.nsukaddition.mixin.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarCabinetBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarCabinetBlockEntity;
import com.xy2407.nsukaddition.common.compat.vinerykaleidoscope.VineryKaleidoscopeCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Kaleidoscope 酒柜接受 Vinery 酒瓶放入。
 * 通过在 onClick 头部拦截 Vinery 酒瓶分支，避免使用未注册的 BottleBlock 实例作为标记
 * （NeoForge 注册表冻结阶段会因侵入式持有者未绑定而崩溃）。
 */
@Mixin(BarCabinetBlock.class)
public class KaleidoscopeBarCabinetMixin {

    @Inject(method = "onClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$handleVineryBottleInsert(BarCabinetBlockEntity barCabinet, Player player, ItemStack stack,
                                               boolean isLeftSide, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !VineryKaleidoscopeCompat.isVineryBottle(stack)) {
            return;
        }
        if (barCabinet.isSingle()) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack leftItem = barCabinet.getLeftItem();
        ItemStack rightItem = barCabinet.getRightItem();
        if (!leftItem.isEmpty() && rightItem.isEmpty() && isLeftSide) {
            isLeftSide = false;
        } else if (leftItem.isEmpty() && !rightItem.isEmpty() && !isLeftSide) {
            isLeftSide = true;
        }
        if (isLeftSide) {
            if (leftItem.isEmpty()) {
                barCabinet.setLeftItem(stack.split(1));
                barCabinet.setSingle(false);
                barCabinet.refresh();
                cir.setReturnValue(true);
                return;
            }
        } else {
            if (rightItem.isEmpty()) {
                barCabinet.setRightItem(stack.split(1));
                barCabinet.refresh();
                barCabinet.setSingle(false);
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
