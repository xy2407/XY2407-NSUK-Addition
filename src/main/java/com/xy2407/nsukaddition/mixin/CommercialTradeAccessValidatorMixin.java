package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.server.city.VillageTourismService;
import common.cn.kafei.simukraft.commercial.CommercialTradeAccessValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 商队 leader 交易跳过商业控制箱绑定验证，仅校验玩家距离与视线。 */
@Mixin(value = CommercialTradeAccessValidator.class, remap = false)
public class CommercialTradeAccessValidatorMixin {

    @Inject(method = "canUseTradeMenu", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$bypassForCaravanLeader(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (VillageTourismService.isCaravanLeader(level, workerId)) {
            cir.setReturnValue(true);
        }
    }
}
