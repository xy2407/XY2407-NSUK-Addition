package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.economy.ResidentialRentService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 将每日收入通知中的"企业的税"改为"商业收入"。 */
@Mixin(ResidentialRentService.class)
public class DailyIncomeSummaryMixin {

    @ModifyArg(
            method = "incomeSummary",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 0,
            remap = true
    )
    private static String nsuk$changeIncomeLabel(String key) {
        return "gui.xy2407_nsuk_addition.daily_income.summary";
    }
}
