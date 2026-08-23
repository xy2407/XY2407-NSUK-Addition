package com.xy2407.nsukaddition.mixin.client;

import common.cn.kafei.simukraft.commercial.CommercialTradeUiRoot;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 商店 / 商队交易界面：原版仅在"零售"报价(result.count()==1)时 shift 才批量 64，
 * 导致出售报价(结果是货币、非零售)无法 shift 批量。这里通过修改 tradeSelected 的
 * count 实参，让按住 shift 时任何报价都批量 64，购买与出售都可 shift 一组一组进行。
 */
@Mixin(value = CommercialTradeUiRoot.class, remap = false)
public abstract class CommercialTradeBatchMixin {

    @ModifyArg(method = "onResultSlotMouseDown",
            at = @At(value = "INVOKE",
                    target = "Lcommon/cn/kafei/simukraft/commercial/CommercialTradeUiRoot;tradeSelected(ZI)V"),
            index = 1, remap = false)
    private int nsuk$batchCount(int count) {
        return Screen.hasShiftDown() ? 64 : count;
    }
}